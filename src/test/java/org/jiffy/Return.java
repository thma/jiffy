package org.jiffy;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Return {
  private final Long id;
  private Long orderId;
  private String reason;
  private final LocalDate createdAt;
  private BigDecimal amount;

  public Return(Long id, Long orderId, String reason, LocalDate createdAt, BigDecimal amount) {
    this.id = id;
    this.orderId = orderId;
    this.reason = reason;
    this.createdAt = createdAt;
    this.amount = amount;
  }

  public Long getId() {
    return id;
  }

  public Long getOrderId() {
    return orderId;
  }

  public String getReason() {
    return reason;
  }

  public LocalDate getCreatedAt() {
    return createdAt;
  }

  public BigDecimal getAmount() {
    return amount;
  }
}
