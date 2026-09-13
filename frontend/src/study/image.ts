/**
 * 올리기 전 사진 줄이기 — 브라우저의 canvas 한 번으로 끝낸다.
 *
 * <p>서버에 리사이즈를 두지 않은 이유: 폰 사진 원본은 3~8MB인데 Claude가 실제로 보는 것은 긴 변
 * 1568px까지다. 큰 원본을 굳이 올려 보내면 사용자의 데이터·서버의 메모리·전송 시간을 셋 다 버린다.
 * 여기서 줄이면 장당 수백 KB가 되어 3MB 상한에 여유가 크게 남는다.
 *
 * <p><b>미리보기는 반드시 data URL이다.</b> CSP의 `img-src`가 `'self' data: https:`라 `blob:`이 없어
 * `URL.createObjectURL()`로 만든 주소는 그림이 조용히 안 뜬다(에러도 화면엔 안 보인다).
 */

/** Claude가 실제로 보는 한계 — 이보다 크게 보내도 서버 쪽에서 다시 줄여질 뿐이다. */
const MAX_EDGE = 1568;

/** 손글씨는 글자 획이 살아야 해서 0.85 밑으로 내리지 않는다(0.7이면 연필 선이 뭉갠다). */
const QUALITY = 0.85;

export interface ShrunkImage {
    /** 서버로 보낼 JPEG. */
    blob: Blob;
    /** 화면에 그릴 미리보기(같은 인코딩 결과를 재사용한다 — 두 번 굽지 않는다). */
    dataUrl: string;
    name: string;
}

/**
 * 고른 파일 한 장을 긴 변 {@link MAX_EDGE}px JPEG로 줄인다.
 *
 * <p>`imageOrientation: 'from-image'`가 있어야 폰이 세로로 찍은 사진의 EXIF 회전이 픽셀에 반영된다 —
 * 빠뜨리면 옆으로 누운 손글씨가 그대로 모델에게 간다.
 */
export async function shrinkForUpload(file: File): Promise<ShrunkImage> {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    try {
        const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(bitmap.width * scale));
        canvas.height = Math.max(1, Math.round(bitmap.height * scale));

        const ctx = canvas.getContext('2d');
        if (!ctx) throw new Error('canvas 2d context를 열 수 없어요');
        ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);

        const dataUrl = canvas.toDataURL('image/jpeg', QUALITY);
        return { blob: dataUrlToBlob(dataUrl), dataUrl, name: file.name };
    } finally {
        bitmap.close();
    }
}

/** data URL → Blob. `toBlob`을 따로 부르면 같은 그림을 두 번 인코딩하게 된다. */
function dataUrlToBlob(dataUrl: string): Blob {
    const binary = atob(dataUrl.slice(dataUrl.indexOf(',') + 1));
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
    return new Blob([bytes], { type: 'image/jpeg' });
}
