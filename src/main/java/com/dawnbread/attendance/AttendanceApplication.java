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
		SpringApplication.run(AttendanceApplication.class, args);
	}

}
