package com.ihsanharh.hiveutils.api;

public enum ModResult {
    PASS, // Let it flow normally.
    MODIFIED, // manually send it and kill the original.
    DENY // block it.
}
