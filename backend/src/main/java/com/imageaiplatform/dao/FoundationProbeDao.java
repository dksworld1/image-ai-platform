package com.imageaiplatform.dao;

public interface FoundationProbeDao {
    int insert(String value);

    long countByValue(String value);
}
