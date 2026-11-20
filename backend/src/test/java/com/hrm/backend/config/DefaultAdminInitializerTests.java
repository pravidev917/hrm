package com.hrm.backend.config;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Company;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.CompanyRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A fresh distribution database must seed an administrator inside a company. */
class DefaultAdminInitializerTests {

    @Test
    void seedsTheFirstAdministratorIntoTheExistingCompany() {
        AdminRepository admins = mock(AdminRepository.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        Company company = new Company();
        company.setCompanyId(1L);
        company.setCompanyName("Default Company");

        when(admins.count()).thenReturn(0L);
        when(companies.findFirstByOrderByCompanyIdAsc()).thenReturn(Optional.of(company));
        when(encoder.encode("StrongPass@2026")).thenReturn("encoded");

        DefaultAdminInitializer initializer =
                new DefaultAdminInitializer(admins, companies, encoder);
        ReflectionTestUtils.setField(initializer, "defaultEmail", "First.Admin@Example.com");
        ReflectionTestUtils.setField(initializer, "defaultPassword", "StrongPass@2026");
        ReflectionTestUtils.setField(initializer, "defaultName", "First Admin");

        initializer.run(null);

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(admins).save(saved.capture());

        assertThat(saved.getValue().getCompany()).isSameAs(company);
        assertThat(saved.getValue().getEmail()).isEqualTo("first.admin@example.com");
        assertThat(saved.getValue().getRoleAccessLevel()).isEqualTo("ADMIN");
    }

    @Test
    void createsACompanyWhenTheSchemaHasNone() {
        AdminRepository admins = mock(AdminRepository.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        Company created = new Company();
        created.setCompanyId(7L);
        created.setCompanyName("Default Company");

        when(admins.count()).thenReturn(0L);
        when(companies.findFirstByOrderByCompanyIdAsc()).thenReturn(Optional.empty());
        when(companies.save(any(Company.class))).thenReturn(created);
        when(encoder.encode("StrongPass@2026")).thenReturn("encoded");

        DefaultAdminInitializer initializer =
                new DefaultAdminInitializer(admins, companies, encoder);
        ReflectionTestUtils.setField(initializer, "defaultEmail", "first@example.com");
        ReflectionTestUtils.setField(initializer, "defaultPassword", "StrongPass@2026");
        ReflectionTestUtils.setField(initializer, "defaultName", "First Admin");

        initializer.run(null);

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(admins).save(saved.capture());
        assertThat(saved.getValue().getCompany()).isSameAs(created);
    }

    @Test
    void neverSeedsWhenAnAdministratorAlreadyExists() {
        AdminRepository admins = mock(AdminRepository.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        when(admins.count()).thenReturn(1L);

        DefaultAdminInitializer initializer =
                new DefaultAdminInitializer(admins, companies, encoder);
        initializer.run(null);

        verify(admins, never()).save(any(Admin.class));
    }
}
