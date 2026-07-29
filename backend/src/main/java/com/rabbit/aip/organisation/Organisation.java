package com.rabbit.aip.organisation;

import com.rabbit.aip.common.domain.BaseEntity;
import com.rabbit.aip.user.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "organisations")
public class Organisation extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    protected Organisation() {
    }

    public Organisation(String code, String name, String timezone) {
        this.code = code.toUpperCase();
        this.name = name;
        this.timezone = timezone;
        this.status = AccountStatus.ACTIVE;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
