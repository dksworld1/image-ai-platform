package com.imageaiplatform.auth.dao;

/** 역할 변경과 같은 app 트랜잭션에서 호출하는 추가 전용 감사 저장소입니다. */
public interface AuthAuditDao {
    void recordRoleChange(String actorUsername, String targetUsername, String roleCode, String action);
}
