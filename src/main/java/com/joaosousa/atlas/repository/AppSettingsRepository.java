package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
}
