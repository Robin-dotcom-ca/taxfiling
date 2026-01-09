package com.taxfiling.model;

import com.taxfiling.model.enums.DeductionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "deduction_items",
    indexes = {
        @Index(name = "idx_deduction_filing", columnList = "filing_id"),
        @Index(name = "idx_deduction_type", columnList = "deduction_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeductionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", nullable = false)
    private TaxFiling filing;

    @Enumerated(EnumType.STRING)
    @Column(name = "deduction_type", nullable = false, length = 50)
    private DeductionType deductionType;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = Map.of(); // Receipts, documentation refs
}
