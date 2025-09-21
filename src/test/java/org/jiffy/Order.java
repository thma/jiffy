package org.jiffy;

import java.math.BigDecimal;

public class Order {
  private final Long id;
  private final BigDecimal amount;

  public Order(Long id, BigDecimal quantity) {
    this.id = id;
    this.amount = quantity;
  }

  public Long getId() {
    return id;
  }


  public BigDecimal getAmount() {
    return amount;
  }
}
