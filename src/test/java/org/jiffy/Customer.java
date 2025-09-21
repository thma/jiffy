package org.jiffy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class Customer {

  private final long id;

  public Customer(long id) {
    this.id = id;
  }

  public int calculateScore(List<Order> orders, List<Return> returns) {
    BigDecimal totalOrderValue = orders.stream()
        .map(Order::getAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalReturnValue = returns.stream()
        .map(Return::getAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalReturnValue.equals(BigDecimal.ZERO) || totalOrderValue.equals(BigDecimal.ZERO)) {
      return 100;
    } else {
      return totalOrderValue.subtract(totalReturnValue)
          .divide(totalOrderValue, 4, java.math.RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100))
          .intValue();
    }
  }

  public long getId() {
    return id;
  }
}
