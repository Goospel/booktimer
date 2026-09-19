package com.booktimer.report;

/** 신고 처리 상태(V96). 운영자가 조치하면 RESOLVED, 같은 신고자가 다시 신고하면 OPEN으로 돌아간다. */
public enum ReportStatus {
    OPEN, RESOLVED
}
