package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.QuarantinedEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuarantinedEntryRepository extends JpaRepository<QuarantinedEntry, Long> {

    List<QuarantinedEntry> findByDataOriginAndRecordingMethod(String dataOrigin, String recordingMethod);

    boolean existsByDataOriginAndRecordingMethodAndTypeAndStartTime(
            String dataOrigin, String recordingMethod, String type, String startTime);

    long countByDataOriginAndRecordingMethod(String dataOrigin, String recordingMethod);

    void deleteByDataOriginAndRecordingMethod(String dataOrigin, String recordingMethod);

    /** Counts per source in one query, so listing sources is not N+1. */
    @Query("""
            SELECT q.dataOrigin, q.recordingMethod, COUNT(q)
              FROM QuarantinedEntry q
             GROUP BY q.dataOrigin, q.recordingMethod
            """)
    List<Object[]> countGroupedBySource();
}
