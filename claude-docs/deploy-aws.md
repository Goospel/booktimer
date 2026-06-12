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

## 어디서 실행하나 — 명령의 정체와 셸

이 가이드의 명령은 **내 터미널(셸)에서 실행**한다. "AWS 전용 터미널" 같은 건 없고, 명령 종류가 섞여 있다:

| 명령 | 정체 | 작용 |
|---|---|---|
| `aws ec2 ...`, `aws rds ...`, `aws ecs ...` | **AWS CLI** | 로컬에서 실행 → 인터넷으로 **AWS 계정에 리소스 생성/조회** |
| `docker build/push/login` | **Docker CLI** | 로컬에서 이미지 빌드 → ECR push |
| `export`, `sed`, `cat <<JSON`, `$(...)` | **셸 문법** | 순수 로컬 — 변수/임시 파일 만드는 보조 |

즉 AWS CLI는 **로컬에서 돌지만 효과는 클라우드**다. `aws configure`로 넣은 자격증명으로 AWS API를 호출한다.

> ⚠️ **셸 주의 (Windows)**: 아래 명령은 **bash 문법**(`export`/`sed`/히어독/`$(...)`)이다. **PowerShell에 그대로 붙이면 깨진다.** 다음 중 하나에서 실행:
>
> 1. **AWS CloudShell (추천)** — AWS 콘솔 우측 상단 터미널 아이콘. 브라우저 안 bash 셸로 **AWS CLI 설치·자격증명이 자동**. 로컬 셋업 0, 이 명령들을 그대로 붙여넣으면 된다.
> 2. **Git Bash / WSL** — 로컬 bash. AWS CLI 설치 + `aws configure` 후 실행.
>
> PowerShell만 쓰겠다면 `export`→`$env:`, 히어독→파일, `sed` 부재 등을 직접 바꿔야 해 번거롭다.

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

## 12-1. 무중단 배포 설정 (deploymentConfiguration)

`create-service`에서 `--deployment-configuration`을 생략하면 새 태스크가 healthy 되기 전에
옛 태스크가 사라지는 공백(503)이 생길 수 있고, **circuit breaker(실패 시 자동 롤백)도 꺼져 있다**.
아래 설정을 적용하면 desiredCount=1이어도 무중단으로 롤링된다.

```bash
aws ecs update-service \
  --cluster $APP-cluster --service $APP-service \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=100,deploymentCircuitBreaker={enable=true,rollback=true}" \
  --region $AWS_REGION
```

- `min=100/max=200` → 새 태스크를 **추가로** 띄워 ALB 헬스 통과 후에야 옛 태스크 드레인 → 항상 healthy ≥1.
- `circuitBreaker{enable,rollback}` → 새 태스크 안정화 실패 시 직전 안정 리비전으로 **자동 롤백**.
- 한 번 적용하면 영속된다(평소 배포는 task definition만 교체, 이 설정은 안 건드림 → 드리프트 없음).

> CI로 멱등 적용: `.github/workflows/zero-downtime-config.yml`(workflow_dispatch)이 위 명령을 수행한다.
> OIDC 역할의 기존 `ecs:UpdateService` 권한으로 충분 — 추가 IAM 불필요.

### (선택) 타깃그룹 드레이닝·헬스체크 단축 — 교체 *속도* 최적화

다운타임 원인은 아니지만 배포 체감 속도를 줄인다. **`elasticloadbalancing` 권한이 필요**하므로
GitHub OIDC 역할(`githubActionsDeployRole`)엔 없다 — 적용하려면 먼저 권한을 추가하거나 CloudShell에서 1회 수행.

```bash
TG_ARN=$(aws ecs describe-services --cluster $APP-cluster --services $APP-service \
  --query 'services[0].loadBalancers[0].targetGroupArn' --output text --region $AWS_REGION)

# 연결 드레이닝 300s → 60s
aws elbv2 modify-target-group-attributes --target-group-arn $TG_ARN \
  --attributes Key=deregistration_delay.timeout_seconds,Value=60 --region $AWS_REGION

# 헬스체크 간격 30s → 15s (새 태스크가 더 빨리 트래픽 수신)
aws elbv2 modify-target-group --target-group-arn $TG_ARN \
  --health-check-interval-seconds 15 --healthy-threshold-count 2 --region $AWS_REGION
```

CI 역할로 위를 자동화하려면 `githubActionsDeployRole` 정책에 추가:
`elasticloadbalancing:DescribeTargetGroups`, `elasticloadbalancing:ModifyTargetGroup`,
`elasticloadbalancing:ModifyTargetGroupAttributes` (Resource는 해당 TG ARN 권장).

---

## 12-1b. ECS 오토스케일링 (홍보 전 서버 용량 게이트)

무중단 배포(§12-1)는 *배포 중* 가용성을 지키지만, 평상시 트래픽이 몰리면 단일 태스크(`desired=1`)가
CPU 100%를 쳐 그대로 장애가 된다(자동 확장 없음). 홍보로 한순간에 유입되면 특히 위험하다.
Application Auto Scaling을 붙여 **상시 2태스크(단일 장애점 제거) + 부하 시 최대 4태스크 자동 확장**으로 만든다.
(배경·병목 분석: plan.md "홍보 전 선수과정 — 서버 용량 보강".)

```bash
RESOURCE_ID="service/$APP-cluster/$APP-service"   # service/<클러스터 이름>/<서비스 이름>

# 1) 스케일 대상 등록: desired count를 2~4 범위로
aws application-autoscaling register-scalable-target \
  --service-namespace ecs --resource-id "$RESOURCE_ID" \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 --max-capacity 4 --region $AWS_REGION

# 2) target-tracking 정책: 평균 CPU 70% 목표
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs --resource-id "$RESOURCE_ID" \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name booktimer-cpu70-target-tracking \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration \
    "TargetValue=70.0,PredefinedMetricSpecification={PredefinedMetricType=ECSServiceAverageCPUUtilization}" \
  --region $AWS_REGION
```

- `min=2` → 한 태스크가 죽어도 서비스 유지 + 평소 부하 분산. `max=4` → **순간 최대 4배 컴퓨트 요금**(예산 $50 인지).
- target-tracking은 CPU가 70%를 넘으면 scale-out, 여유로우면 scale-in 한다(기본 cooldown 300초). 더 민감/보수적으로
  바꾸려면 위 설정에 `ScaleOutCooldown`/`ScaleInCooldown`을 더한다.
- 한 번 적용하면 서비스에 영속된다(배포는 task definition만 교체 → 드리프트 없음).

> CI로 멱등 적용: `.github/workflows/autoscaling-config.yml`(workflow_dispatch)이 위를 수행하고 before/after를 출력한다.
> **단, OIDC 역할(`githubActionsDeployRole`)에 권한 추가가 선결**이다 — §12-1 무중단 배포는 `ecs:UpdateService`로
> 충분했지만 오토스케일링은 아니다(target-tracking이 CloudWatch 알람을 자동 생성하기 때문):

```bash
cat > /tmp/gh-autoscaling.json <<JSON
{ "Version": "2012-10-17", "Statement": [
  { "Effect": "Allow", "Action": [
      "application-autoscaling:RegisterScalableTarget",
      "application-autoscaling:DeregisterScalableTarget",
      "application-autoscaling:DescribeScalableTargets",
      "application-autoscaling:PutScalingPolicy",
      "application-autoscaling:DescribeScalingPolicies",
      "application-autoscaling:DeleteScalingPolicy",
      "cloudwatch:PutMetricAlarm","cloudwatch:DescribeAlarms","cloudwatch:DeleteAlarms"
    ], "Resource": "*" }
] }
JSON
aws iam put-role-policy --role-name githubActionsDeployRole \
  --policy-name booktimer-autoscaling --policy-document file:///tmp/gh-autoscaling.json
```

> ⚠️ 첫 `register-scalable-target` 시 service-linked role `AWSServiceRoleForApplicationAutoScaling_ECSService`가
> 없으면 자동 생성을 시도하는데, 워크플로 OIDC 역할엔 그 생성 권한이 없어
> `ValidationException: User is missing ... iam:CreateServiceLinkedRole`로 **실패한다**(BookTimer 실제 첫 실행에서 발생, T-045).
> **권장 해결 = CloudShell에서 그 role을 직접 1회 생성**(워크플로 역할엔 IAM 생성 권한을 안 줘 최소권한 유지 — 한 번
> 만들면 이후엔 이미 존재해 워크플로가 생성 시도조차 안 한다):
>
> ```bash
> aws iam create-service-linked-role --aws-service-name ecs.application-autoscaling.amazonaws.com
> # 이미 있으면 "has been taken" 에러가 나는데 무시(있으면 그걸로 충분).
> ```
>
> (대안: 워크플로 역할 정책에 `iam:CreateServiceLinkedRole`을 더해도 되지만, OIDC 역할에 IAM 쓰기 권한을 주는
> 셈이라 위 직접 생성이 깔끔하다.) 생성 후 워크플로 재실행 → before/after describe로 Min2/Max4/CPU70 확인.

### 검증

```bash
# 적용 확인(스케일 타깃·정책)
aws application-autoscaling describe-scalable-targets \
  --service-namespace ecs --resource-ids "$RESOURCE_ID" --region $AWS_REGION

# 태스크가 2개로 늘었는지
aws ecs describe-services --cluster $APP-cluster --services $APP-service \
  --query 'services[0].{desired:desiredCount,running:runningCount}' --region $AWS_REGION
```

부하를 줘 실제 scale-out(2→3→4)·latency 곡선을 보려면 `load-test/booktimer-load.js`(k6) 사용. ⚠️ 운영 대상이라
저부하부터 — 스크립트 상단 주의사항 참고.

---

## 12-2. 알라딘 도서 검색(TTBKey) 연동

책장의 검색·제휴 구매링크는 알라딘 OpenAPI를 쓴다. 앱은 `BOOKTIMER_ALADIN_TTB_KEY` 환경변수를
읽어(`@Value("${booktimer.aladin.ttb-key:not-configured}")`), 없으면 검색을 끄고 수동 입력으로 폴백한다.

### ① TTBKey 발급 (외부, 1회)
1. <https://www.aladin.co.kr/ttb/wblog_manage.aspx> (알라딘 → 마이페이지 → 외부 서비스/OpenAPI)에서 OpenAPI 사용 신청.
2. 발급된 **TTBKey** 복사. (제휴 수익은 이 키가 실린 구매 링크 클릭/구매로 적립 — 제휴 약관 확인.)

### ② SSM에 키 저장 (배포보다 먼저!)
ECS `secrets`는 태스크 시작 시 SSM에서 **필수로** 당겨오므로, 파라미터가 없으면 새 태스크가
기동 실패한다(T-011). 따라서 **반드시 먼저** 만든다. CloudShell에서:
```bash
aws ssm put-parameter --name /booktimer/ALADIN_TTB_KEY \
  --value "ttb본인키여기" --type SecureString --region $AWS_REGION
```
> 기본 KMS(aws/ssm) 암호화면 실행역할의 기존 `ssm:GetParameters`(/booktimer/*)로 복호화된다(추가 권한 불필요).

### ③ task-definition에 시크릿 참조 + 배포
`deploy/task-definition.json`의 `secrets`에 이미 추가돼 있다:
```json
{ "name": "BOOKTIMER_ALADIN_TTB_KEY", "valueFrom": ".../parameter/booktimer/ALADIN_TTB_KEY" }
```
②가 끝난 뒤 main에 배포가 돌면 새 태스크가 키를 주입받아 **검색이 라이브로 활성화**된다.
확인: `/books`에서 검색창이 보이고(수동 입력 폴백이 아니라), 검색 결과가 나오면 성공.

> 로컬에서 테스트하려면 `BOOKTIMER_ALADIN_TTB_KEY=ttb...`를 환경변수로 주고 `bootRun`.

---

## 12-3. 책BTI LLM(Gemini) 키 연동

책BTI(독서 성향 분석, Phase 3~5)는 Gemini Flash로 성향 서술을 생성한다. 앱은 `BOOKTIMER_LLM_API_KEY`
환경변수를 읽어(`@Value("${booktimer.llm.api-key:not-configured}")`), 없으면 서술을 끄고 **사실만 표시(폴백)** 한다.
즉 키가 없어도 화면·캐시·콜드스타트는 정상 동작하고, 키가 들어오면 서술이 라이브로 켜진다.

### ① API 키 발급 (외부, 1회)
1. <https://aistudio.google.com/> → Google 계정 로그인 → **"Get API key" → "Create API key"**.
2. 발급된 키(`AIza...` 또는 신형 `AQ....`) 복사. 무료 티어로 시작 가능(분당 요청 제한 있음).
   > 신형 `AQ.` 키는 헤더 인증이 막혀 있다 — 아래 "AQ 키 주의(T-037)" 참고. 어댑터는 호환되게 호출한다.
> 무료 티어는 프롬프트가 모델 개선에 쓰일 수 있다 — 프롬프트엔 집계된 사실(장르명·저자명·권수)만 들어가고
> 원문/PII는 없지만, 운영 본격화 땐 학습 제외(유료) 티어를 고려한다.

### ② SSM에 키 저장 (배포보다 먼저!)
ECS `secrets`는 태스크 시작 시 SSM에서 **필수로** 당겨오므로, 파라미터가 없으면 새 태스크가 기동 실패한다(T-011).
**반드시 먼저** 만든다. CloudShell에서:
```bash
aws ssm put-parameter --name /booktimer/LLM_API_KEY \
  --value "AIza본인키여기" --type SecureString --region $AWS_REGION
# 갱신(재발급/회전) 시엔 --overwrite 추가
```
> 기본 KMS(aws/ssm) 암호화면 실행역할의 기존 `ssm:GetParameters`(/booktimer/*)로 복호화된다(추가 권한 불필요).
> 키 값은 셸 명령에 평문으로 남으니(히스토리) 노출되면 AI Studio에서 폐기→재발급 후 `--overwrite`로 교체한다.

### ③ task-definition에 시크릿 참조 + 배포
`deploy/task-definition.json`의 `secrets`에 추가돼 있다:
```json
{ "name": "BOOKTIMER_LLM_API_KEY", "valueFrom": ".../parameter/booktimer/LLM_API_KEY" }
```
②가 끝난 뒤 main에 배포가 돌면 새 태스크가 키를 주입받아 **성향 서술이 라이브로 활성화**된다.
확인: `/personality`에서 책이 임계(5권) 이상일 때 서술 문단이 뜨면 성공(폴백 문구가 아니라). 안 뜨면
`aws logs tail /ecs/booktimer --follow --region $AWS_REGION`로 "Gemini 서술 생성 실패" 로그 확인(키 오타·모델명·rate limit).

### (선택) 모델 변경
기본은 `gemini-2.5-flash`(`@Value("${booktimer.llm.model:gemini-2.5-flash}")`). 코드 수정 없이 바꾸려면
`BOOKTIMER_LLM_MODEL`을 env로 주입한다(예: `gemini-2.0-flash`). 평문이라 SSM SecureString이 아니라
task-definition `environment`에 둬도 된다.

### ⚠️ AQ 키 주의 — 인증은 `?key=` 쿼리파라미터 (T-037)
Google이 2026년 들어 API 키를 구형 `AIza…`(Traffic key)에서 신형 `AQ.…`(Authentication key)로 옮기는 중인데,
**일부 계정은 `AQ.` 키만 발급**된다. 이 `AQ.` 키는 `x-goog-api-key` 헤더로 보내면
`401 ACCESS_TOKEN_TYPE_UNSUPPORTED`로 거부되고, `Authorization: Bearer`도 401(UNAUTHENTICATED)이다.
**`?key=…` 쿼리파라미터 방식만 통한다.** 어댑터는 이 방식으로 호출하므로 `AIza`·`AQ.` 둘 다 동작한다.
키 검증(CloudShell): `curl -s "https://generativelanguage.googleapis.com/v1beta/models?key=본인키&pageSize=1"`
→ 모델 목록 JSON이 나오면 정상. (헤더로 테스트하면 AQ 키는 멀쩡해도 실패하니 헷갈리지 말 것.)

> 로컬 테스트: `BOOKTIMER_LLM_API_KEY=AIza...`를 환경변수로 주고 `bootRun`.

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
