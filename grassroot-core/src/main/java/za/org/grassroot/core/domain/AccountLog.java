package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2016/04/03.
 */
@Entity
@Table(name="account_log",
        uniqueConstraints = {@UniqueConstraint(name = "uk_account_log_uid", columnNames = "uid")})
public class AccountLog implements ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @ManyToOne
    private Account account;

    @Column(name="creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @Enumerated(EnumType.STRING)
    @Column(name="account_log_type", nullable = false, length = 50)
    private AccountLogType accountLogType;

    @Column(name="user_uid", nullable = false)
    private String userUid;

    @Column(name="group_uid")
    private String groupUid;

    @Column(name="paid_group_uid")
    private String paidGroupUid;

    @Column(name="description", length = 255)
    private String description;

    @Column(name="reference_amount")
    private Long amountBilledOrPaid;

    public static class Builder {
        private Account account;
        private String userUid;
        private AccountLogType accountLogType;
        private String groupUid;
        private String paidGroupUid;
        private String description;
        private Long amountBilledOrPaid;

        public Builder(Account account) {
            this.account = account;
        }

        public Builder userUid(String userUid) {
            this.userUid = userUid;
            return this;
        }

        public Builder accountLogType(AccountLogType accountLogType) {
            this.accountLogType = accountLogType;
            return this;
        }

        public Builder groupUid(String groupUid) {
            this.groupUid = groupUid;
            return this;
        }

        public Builder paidGroupUid(String paidGroupUid) {
            this.paidGroupUid = paidGroupUid;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder billedOrPaid(Long amountBilledOrPaid) {
            this.amountBilledOrPaid = amountBilledOrPaid;
            return this;
        }

        public AccountLog build() {
            Objects.requireNonNull(account);
            Objects.requireNonNull(userUid);
            Objects.requireNonNull(accountLogType);

            AccountLog accountLog = new AccountLog(account, userUid, accountLogType);
            accountLog.description = description;
            accountLog.groupUid = groupUid;
            accountLog.paidGroupUid = paidGroupUid;
            accountLog.amountBilledOrPaid = amountBilledOrPaid;

            return accountLog;
        }
    }

    private AccountLog() {
        // for JPA
    }

    private AccountLog(Account account, String userUid, AccountLogType accountLogType) {
        this.uid = UIDGenerator.generateId();
        this.account = account;
        this.userUid = userUid;
        this.accountLogType = accountLogType;
        this.creationTime = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Account getAccount() { return account; }

    public Instant getCreationTime() {
        return creationTime;
    }

    public AccountLogType getAccountLogType() {
        return accountLogType;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getGroupUid() { return groupUid; }

    public String getPaidGroupUid() { return paidGroupUid; }

    public String getDescription() {
        return description;
    }

    public Long getAmountBilledOrPaid() {
        return amountBilledOrPaid;
    }

    @Override
    public int hashCode() {
        return (getUid() != null) ? getUid().hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        final AccountLog that = (AccountLog) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;
    }

    @Override
    public String toString() {
        return "AccountLog{" +
                "id=" + id +
                ", creationTime =" + creationTime +
                ", userUid=" + userUid +
                ", groupUid=" + groupUid +
                ", accountLogType=" + accountLogType +
                ", description='" + description + '\'' +
                '}';
    }
}
