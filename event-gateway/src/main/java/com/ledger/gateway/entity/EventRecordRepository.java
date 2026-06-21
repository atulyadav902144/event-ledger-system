package com.ledger.gateway.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRecordRepository extends JpaRepository<EventRecord, Long>
{
    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);

    Optional<EventRecord> findByEventId(String eventId);
}