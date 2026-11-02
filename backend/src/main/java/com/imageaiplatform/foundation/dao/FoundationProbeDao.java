package com.imageaiplatform.foundation.dao;

public interface FoundationProbeDao {
    int insert(String value);

    long countByValue(String value);
}

