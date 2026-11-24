package dev.skillsieve.core.insight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InsightServiceTest {

  @ParameterizedTest
  @CsvSource({"30,1,true", "29,0,false", "80,2,false", "100,0,true"})
  void shouldApplyGapRule(int pct, int level, boolean expected) {
    assertThat(InsightService.isGap(pct, level)).isEqualTo(expected);
  }
}
