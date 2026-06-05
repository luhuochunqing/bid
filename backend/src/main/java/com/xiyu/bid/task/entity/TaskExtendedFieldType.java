package com.xiyu.bid.task.entity;

/**
 * Task extended field type (matches V103 CHECK constraint).
 *
 * <p>Enum value names are intentionally <em>lowercase</em> to match the DB
 * storage format required by the V103 CHECK constraint
 * ({@code field_type IN ('text','textarea','number','date','select')}).
 * This violates the standard Java naming convention for enum constants,
 * but avoids the need for a custom {@code AttributeConverter} on every
 * persisted entity that references this type.</p>
 */
public enum TaskExtendedFieldType {
    /** Single-line text input. */
    text,
    /** Multi-line text area. */
    textarea,
    /** Numeric input. */
    number,
    /** Date picker. */
    date,
    /** Dropdown select (options defined via {@code options_json}). */
    select
}
