package com.booktimer.session;

/**
 * 하루 기록 안의 책 한 권 — <b>무슨 책을 얼마나</b>.
 *
 * <p>예전 {@link DailyReadingRecord}는 제목만({@code List<String>}) 담았다. 그래서 기록 화면은
 * (1) 표지를 못 세우고 첫 글자 상자로 대신했고 (2) 하루에 여러 권을 읽어도 <b>어느 책을 오래 읽었는지</b>를
 * 말할 수 없었다 — 그날 총합 하나뿐이었다. 세 값이 한 줄에 같이 와야 화면이 책 더미를 펼쳐 보여줄 수 있다.
 *
 * @param title    책 제목
 * @param coverUrl 표지 주소. 없으면 {@code null} — 화면이 제목색 첫 글자로 떨어뜨린다.
 * @param seconds  그날 <b>이 책만</b> 읽은 시간(초). 같은 책의 여러 세션은 한 줄로 합산된다.
 */
public record BookRead(String title, String coverUrl, long seconds) {
}
