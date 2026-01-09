package com.taxfiling.model;

import com.taxfiling.model.enums.IncomeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "income_items",
    indexes = {
        @Index(name = "idx_income_filing", columnList = "filing_id"),
        @Index(name = "idx_income_type", columnList = "income_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomeItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", nullable = false)
    private TaxFiling filing;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_type", nullable = false, length = 50)
    private IncomeType incomeType;

    @Column(length = 255)
    private String source; // Employer name, etc.

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_withheld", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxWithheld = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = Map.of(); // T4 slip details, box numbers, etc.
}
