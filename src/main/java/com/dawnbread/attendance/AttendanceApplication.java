package com.dawnbread.attendance;

import com.dawnbread.attendance.repository.TenantScopedRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(
        basePackages = "com.dawnbread.attendance.repository",
        repositoryBaseClass = TenantScopedRepositoryImpl.class)
public class AttendanceApplication {

	public static void main(String[] args) {
		// Must run before anything reads the clock — see ServerTimeZone for why.
		com.dawnbread.attendance.util.ServerTimeZone.pinToUtc();
		SpringApplication.run(AttendanceApplication.class, args);
	}

}
