package com.howaboutus.backend.bookmarks.controller.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NotBlank(message = "name은 공백일 수 없습니다")
@Size(max = 50, message = "name은 50자 이하여야 합니다")
@Pattern(regexp = "[\\p{L}\\p{N} _\\-]+", message = "name에 허용되지 않는 문자가 포함되어 있습니다")
@Constraint(validatedBy = {})
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BookmarkCategoryName {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
