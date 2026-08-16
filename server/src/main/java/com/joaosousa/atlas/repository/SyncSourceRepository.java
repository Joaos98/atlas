package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.SyncSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncSourceRepository extends JpaRepository<SyncSource, SyncSource.Key> {

    List<SyncSource> findAllByOrderByFirstSeenAsc();
}
