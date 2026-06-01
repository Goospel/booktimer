# AWS 배포 가이드 — ECS Fargate + ALB + RDS (Phase 1)

> BookTimer를 **ECS Fargate**로 배포하기 위한 AWS 리소스 생성 체크리스트.
> 이 문서는 **사용자가 직접 콘솔/CLI로 실행**하는 단계다. CI/CD 워크플로(자동 배포)는 Phase 2.
> 명령은 **AWS CLI v2** 기준이며, 콘솔로 해도 동일한 설정값을 쓰면 된다.

## 목표 아키텍처

```
GitHub Actions ──(OIDC)──▶ ECR(이미지) ──▶ ECS Fargate(태스크)
                                              │
인터넷 ─▶ ALB(80) ─▶ 타깃그룹(8080) ─▶ ECS 태스크 ─▶ RDS MySQL(3306)
                                              └─ 로그 ─▶ CloudWatch
                          시크릿(DB접속) ◀─ SSM Parameter Store
```

- **컴퓨트**: ECS Fargate (서버 관리 없음, desired=1 자동 복구)
- **인그레스**: ALB → 타깃그룹(IP 타입, 8080) — 안정적 DNS, `/actuator/health` 헬스체크
- **시크릿**: SSM Parameter Store(SecureString) → 태스크 실행역할이 읽어 env 주입
- **인증**: GitHub OIDC 역할(장기 키 저장 X)

> ⚠️ **비용**: Fargate(0.25vCPU/0.5GB 24/7 ≈ 월 $10 안팎) + ALB(≈ 월 $16) + RDS(db.t3.micro 프리티어 12개월). **프리티어 아닌 항목 있음** — 다 써보면 맨 아래 **teardown** 순서대로 정리하세요. 가격은 리전·시점 따라 변하니 콘솔에서 확인.

---

## 0. 사전 준비

```bash
# AWS CLI 설치 확인 + 자격증명(관리자급 IAM 사용자/SSO)으로 로그인
aws --version
aws configure        # 또는 aws sso login

# 이 가이드에서 쓸 변수 (셸에 export 해두면 이후 명령에 그대로 쓰임)
export AWS_REGION=ap-northeast-2          # 서울. 원하는 리전으로
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export APP=booktimer
echo "Account=$ACCOUNT_ID Region=$AWS_REGION"

# 기본 VPC와 서브넷(간단하게 기본 VPC 사용)
export VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text --region $AWS_REGION)
export SUBNETS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID \
  --query 'Subnets[].SubnetId' --output text --region $AWS_REGION)
echo "VPC=$VPC_ID Subnets=$SUBNETS"
```

> GitHub 저장소 식별자도 메모: `OWNER/REPO` = `Goospel/booktimer` (OIDC 신뢰정책에 사용).

---

## 1. 보안그룹 3개 (계층 방화벽)

규칙: 인터넷 → **alb-sg**(80) → **ecs-sg**(8080) → **rds-sg**(3306). 각 계층은 바로 앞 계층에서만 받는다.

```bash
# 생성
export ALB_SG=$(aws ec2 create-security-group --group-name $APP-alb-sg \
  --description "ALB ingress" --vpc-id $VPC_ID --region $AWS_REGION --query GroupId --output text)
export ECS_SG=$(aws ec2 create-security-group --group-name $APP-ecs-sg \
  --description "ECS task" --vpc-id $VPC_ID --region $AWS_REGION --query GroupId --output text)
export RDS_SG=$(aws ec2 create-security-group --group-name $APP-rds-sg \
  --description "RDS mysql" --vpc-id $VPC_ID --region $AWS_REGION --query GroupId --output text)

# 규칙: ALB는 인터넷에서 80
aws ec2 authorize-security-group-ingress --group-id $ALB_SG \
  --protocol tcp --port 80 --cidr 0.0.0.0/0 --region $AWS_REGION
# ECS는 ALB에서만 8080
aws ec2 authorize-security-group-ingress --group-id $ECS_SG \
  --protocol tcp --port 8080 --source-group $ALB_SG --region $AWS_REGION
# RDS는 ECS에서만 3306
aws ec2 authorize-security-group-ingress --group-id $RDS_SG \
  --protocol tcp --port 3306 --source-group $ECS_SG --region $AWS_REGION

echo "ALB_SG=$ALB_SG ECS_SG=$ECS_SG RDS_SG=$RDS_SG"
```

---

## 2. ECR 저장소

```bash
aws ecr create-repository --repository-name $APP \
  --image-scanning-configuration scanOnPush=true --region $AWS_REGION
# 이미지 URI: $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/booktimer
```

---

## 3. RDS MySQL  *(생성에 ~10분 — 먼저 걸어두고 다음 단계 진행)*

```bash
# DB 마스터 비밀번호 — 직접 강한 값으로. (예시값 그대로 쓰지 말 것)
export DB_PASSWORD='CHANGE_me_strong_pw_123!'

aws rds create-db-instance \
  --db-instance-identifier $APP-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --master-username admin \
  --master-user-password "$DB_PASSWORD" \
  --allocated-storage 20 \
  --db-name booktimer \
  --vpc-security-group-ids $RDS_SG \
  --no-publicly-accessible \
  --backup-retention-period 1 \
  --region $AWS_REGION

# 완료 대기 + 엔드포인트 확보
aws rds wait db-instance-available --db-instance-identifier $APP-db --region $AWS_REGION
export RDS_ENDPOINT=$(aws rds describe-db-instances --db-instance-identifier $APP-db \
  --query 'DBInstances[0].Endpoint.Address' --output text --region $AWS_REGION)
echo "RDS=$RDS_ENDPOINT"
```

> `--no-publicly-accessible` + rds-sg(ECS만 허용)이라 외부에서 직접 접속 불가 — 의도된 설계.

---

## 4. CloudWatch 로그 그룹

```bash
aws logs create-log-group --log-group-name /ecs/$APP --region $AWS_REGION
```

---

## 5. SSM Parameter Store — DB 접속 시크릿  *(RDS 엔드포인트 나온 뒤)*

```bash
aws ssm put-parameter --name /$APP/SPRING_DATASOURCE_URL --type SecureString \
  --value "jdbc:mysql://$RDS_ENDPOINT:3306/booktimer?useSSL=true&serverTimezone=UTC" --region $AWS_REGION
aws ssm put-parameter --name /$APP/SPRING_DATASOURCE_USERNAME --type SecureString \
  --value "admin" --region $AWS_REGION
aws ssm put-parameter --name /$APP/SPRING_DATASOURCE_PASSWORD --type SecureString \
  --value "$DB_PASSWORD" --region $AWS_REGION
```

> 시크릿은 여기(SSM)에만 둔다. **코드/이미지/리포에 절대 커밋 금지**(N-013).

---

## 6. IAM 역할

### 6-1. ECS 태스크 실행역할 (ecsTaskExecutionRole)

이미지 pull + SSM 시크릿 읽기 + 로그 쓰기 권한.

```bash
# 신뢰정책: ecs-tasks가 assume
cat > /tmp/ecs-trust.json <<'JSON'
{ "Version": "2012-10-17", "Statement": [{
  "Effect": "Allow", "Principal": { "Service": "ecs-tasks.amazonaws.com" },
  "Action": "sts:AssumeRole" }] }
JSON
aws iam create-role --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file:///tmp/ecs-trust.json || echo "이미 있으면 무시"
aws iam attach-role-policy --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

# SSM 시크릿 읽기 인라인 정책 (기본 정책엔 없음)
cat > /tmp/ssm-read.json <<JSON
{ "Version": "2012-10-17", "Statement": [{
  "Effect": "Allow",
  "Action": ["ssm:GetParameters"],
  "Resource": "arn:aws:ssm:$AWS_REGION:$ACCOUNT_ID:parameter/$APP/*" }] }
JSON
aws iam put-role-policy --role-name ecsTaskExecutionRole \
  --policy-name booktimer-ssm-read --policy-document file:///tmp/ssm-read.json
```

> SecureString을 **기본 KMS 키(aws/ssm)** 로 암호화했다면 추가 kms 권한 없이도 `GetParameters`가 복호화한다. 커스텀 CMK를 썼다면 `kms:Decrypt`를 따로 허용해야 한다.

### 6-2. GitHub OIDC 자격증명 + 배포역할 (Phase 2 CI가 사용)

```bash
# OIDC 자격증명 공급자 (계정에 한 번만)
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 || echo "이미 있으면 무시"

# 신뢰정책: 우리 레포의 Actions만 assume 가능
cat > /tmp/gh-trust.json <<JSON
{ "Version": "2012-10-17", "Statement": [{
  "Effect": "Allow",
  "Principal": { "Federated": "arn:aws:iam::$ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com" },
  "Action": "sts:AssumeRoleWithWebIdentity",
  "Condition": {
    "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
    "StringLike": { "token.actions.githubusercontent.com:sub": "repo:Goospel/booktimer:*" }
  } }] }
JSON
aws iam create-role --role-name githubActionsDeployRole \
  --assume-role-policy-document file:///tmp/gh-trust.json

# 배포 권한: ECR push + ECS 배포 + PassRole(실행역할 넘기기) + 로그
cat > /tmp/gh-perms.json <<JSON
{ "Version": "2012-10-17", "Statement": [
  { "Effect": "Allow", "Action": [
      "ecr:GetAuthorizationToken","ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload","ecr:UploadLayerPart","ecr:CompleteLayerUpload",
      "ecr:PutImage","ecr:BatchGetImage","ecr:GetDownloadUrlForLayer"
    ], "Resource": "*" },
  { "Effect": "Allow", "Action": [
      "ecs:RegisterTaskDefinition","ecs:DescribeTaskDefinition",
      "ecs:UpdateService","ecs:DescribeServices"
    ], "Resource": "*" },
  { "Effect": "Allow", "Action": "iam:PassRole",
    "Resource": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole" }
] }
JSON
aws iam put-role-policy --role-name githubActionsDeployRole \
  --policy-name booktimer-deploy --policy-document file:///tmp/gh-perms.json

echo "DEPLOY_ROLE_ARN=arn:aws:iam::$ACCOUNT_ID:role/githubActionsDeployRole"   # Phase 2 시크릿에 사용
```

---

## 7. 부트스트랩 이미지 push (서비스가 뜰 첫 이미지)

ECS 서비스를 만들려면 ECR에 이미지가 하나 있어야 한다. 로컬에서 한 번 빌드·push:

```bash
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

docker build -t $APP:bootstrap .
docker tag $APP:bootstrap $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$APP:latest
docker push $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$APP:latest
```

---

## 8. ALB + 타깃그룹 + 리스너

```bash
# ALB (퍼블릭 서브넷 2개 이상 필요 — 기본 VPC 서브넷 사용)
export ALB_ARN=$(aws elbv2 create-load-balancer --name $APP-alb \
  --subnets $SUBNETS --security-groups $ALB_SG --type application \
  --region $AWS_REGION --query 'LoadBalancers[0].LoadBalancerArn' --output text)

# 타깃그룹: IP 타입(Fargate awsvpc), 8080, 헬스체크 /actuator/health
export TG_ARN=$(aws elbv2 create-target-group --name $APP-tg \
  --protocol HTTP --port 8080 --vpc-id $VPC_ID --target-type ip \
  --health-check-path /actuator/health --health-check-interval-seconds 30 \
  --healthy-threshold-count 2 --region $AWS_REGION \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

# 리스너: 80 → 타깃그룹
aws elbv2 create-listener --load-balancer-arn $ALB_ARN \
  --protocol HTTP --port 80 \
  --default-actions Type=forward,TargetGroupArn=$TG_ARN --region $AWS_REGION

export ALB_DNS=$(aws elbv2 describe-load-balancers --load-balancer-arns $ALB_ARN \
  --query 'LoadBalancers[0].DNSName' --output text --region $AWS_REGION)
echo "접속주소(배포 후): http://$ALB_DNS"
```

---

## 9. ECS 클러스터

```bash
aws ecs create-cluster --cluster-name $APP-cluster --region $AWS_REGION
```

---

## 10. 태스크 정의 등록

리포의 `deploy/task-definition.json`에서 `<ACCOUNT_ID>`/`<REGION>` 치환 후 등록:

```bash
sed -e "s/<ACCOUNT_ID>/$ACCOUNT_ID/g" -e "s/<REGION>/$AWS_REGION/g" \
  deploy/task-definition.json > /tmp/td.json
aws ecs register-task-definition --cli-input-json file:///tmp/td.json --region $AWS_REGION
```

---

## 11. ECS 서비스 생성

```bash
# 서브넷을 콤마구분으로 (CLI 형식)
export SUBNET_CSV=$(echo $SUBNETS | tr ' ' ',')

aws ecs create-service \
  --cluster $APP-cluster \
  --service-name $APP-service \
  --task-definition booktimer-task \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_CSV],securityGroups=[$ECS_SG],assignPublicIp=ENABLED}" \
  --load-balancers "targetGroupArn=$TG_ARN,containerName=booktimer,containerPort=8080" \
  --health-check-grace-period-seconds 120 \
  --region $AWS_REGION
```

> `assignPublicIp=ENABLED` — 기본 VPC 퍼블릭 서브넷에서 ECR/SSM에 닿으려면 필요(별도 NAT 없이).
> `grace-period 120` — Spring Boot 부팅 동안 ALB 헬스체크 실패로 죽이지 않도록 유예.

---

## 12. 검증

```bash
# 서비스 안정화 대기 (태스크가 RUNNING + 타깃 healthy)
aws ecs wait services-stable --cluster $APP-cluster --services $APP-service --region $AWS_REGION

# 헬스체크
curl -s http://$ALB_DNS/actuator/health      # {"status":"UP"} 기대
```

브라우저에서 `http://<ALB_DNS>/signup` → 가입 → 로그인 → 대시보드 → 측정 시작/종료까지 직접 확인.

문제 시: `aws logs tail /ecs/$APP --follow --region $AWS_REGION` 로 컨테이너 로그 확인.

---

## Phase 2에서 쓸 GitHub Secrets (미리 메모)

CI/CD 워크플로(다음 단계)에서 저장소 Settings → Secrets에 등록할 값:

| Secret | 값 |
|---|---|
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::<ACCOUNT_ID>:role/githubActionsDeployRole` |
| `ECR_REPOSITORY` | `booktimer` |
| `ECS_CLUSTER` | `booktimer-cluster` |
| `ECS_SERVICE` | `booktimer-service` |

> DB 시크릿은 SSM에 있으므로 GitHub엔 넣지 않는다(태스크 실행역할이 SSM에서 읽음).

---

## 💸 Teardown (다 써본 뒤 — 과금 중단)

생성 역순으로:

```bash
aws ecs update-service --cluster $APP-cluster --service $APP-service --desired-count 0 --region $AWS_REGION
aws ecs delete-service --cluster $APP-cluster --service $APP-service --force --region $AWS_REGION
aws elbv2 delete-listener --listener-arn <LISTENER_ARN> --region $AWS_REGION
aws elbv2 delete-load-balancer --load-balancer-arn $ALB_ARN --region $AWS_REGION
aws elbv2 delete-target-group --target-group-arn $TG_ARN --region $AWS_REGION
aws ecs delete-cluster --cluster $APP-cluster --region $AWS_REGION
aws rds delete-db-instance --db-instance-identifier $APP-db --skip-final-snapshot --region $AWS_REGION
aws ecr delete-repository --repository-name $APP --force --region $AWS_REGION
aws logs delete-log-group --log-group-name /ecs/$APP --region $AWS_REGION
# SSM 파라미터, 보안그룹, IAM 역할/정책도 정리
```

> **ALB·RDS·Fargate가 주 과금원** — 이 셋만 지워도 대부분 멈춘다. 콘솔 Billing에서 확인.
