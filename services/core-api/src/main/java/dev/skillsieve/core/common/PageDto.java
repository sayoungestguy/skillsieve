package dev.skillsieve.core.common;

import java.util.List;

public record PageDto<T>(List<T> content, long totalElements, int page, int size) {

  public PageDto {
    content = List.copyOf(content);
  }
}
