-- MySQL 8.0 baseline for new customer deployments.
-- MySQL 8.0 is the single supported migration path; future changes belong in db/migration-mysql.
set names utf8mb4;

    create table accounts (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        industry varchar(100),
        region varchar(100),
        name varchar(200) not null,
        contact_info varchar(500),
        credit_level enum ('A','B','C','D') not null,
        type enum ('CLIENT','SUPPLIER','PARTNER','GOVERNMENT','OTHER') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table ai_analysis_jobs (
        completed_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        requested_by bigint,
        target_id bigint not null,
        error_message varchar(1000),
        analysis_type enum ('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') not null,
        status enum ('PENDING','COMPLETED','FAILED') not null,
        target_type enum ('TENDER','PROJECT') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table ai_analysis_results (
        score integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        job_id bigint,
        project_id bigint,
        tender_id bigint,
        risk_level varchar(30),
        suggestion varchar(500),
        payload_json TEXT not null,
        analysis_type enum ('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table alert_history (
        resolved bit not null,
        acknowledged_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        resolved_at datetime(6),
        rule_id bigint not null,
        related_id varchar(100),
        message TEXT not null,
        level enum ('LOW','MEDIUM','HIGH','CRITICAL') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table alert_rules (
        enabled bit not null,
        threshold decimal(19,2) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        created_by varchar(100) not null,
        name varchar(200) not null,
        `condition` enum ('GREATER_THAN','LESS_THAN','EQUALS','CONTAINS') not null,
        type enum ('DEADLINE','BUDGET','RISK','DOCUMENT','QUALIFICATION_EXPIRY','DEPOSIT_RETURN') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table approval_actions (
        action_time datetime(6) not null,
        actor_id bigint not null,
        created_at datetime(6) not null,
        approval_request_id binary(16) not null,
        id binary(16) not null,
        actor_name varchar(100) not null,
        comment TEXT,
        action_type enum ('SUBMIT','APPROVE','REJECT','CANCEL') not null,
        new_status enum ('PENDING','APPROVED','REJECTED','CANCELLED'),
        previous_status enum ('PENDING','APPROVED','REJECTED','CANCELLED'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table approval_requests (
        is_read bit,
        priority integer not null,
        completed_at datetime(6),
        created_at datetime(6) not null,
        created_by bigint,
        current_approver_id bigint,
        due_date datetime(6),
        project_id bigint not null,
        requester_id bigint not null,
        submitted_at datetime(6) not null,
        updated_at datetime(6),
        updated_by bigint,
        id binary(16) not null,
        approval_type varchar(50) not null,
        current_approver_name varchar(100),
        requester_name varchar(100) not null,
        project_name varchar(200),
        title varchar(200) not null,
        attachment_ids TEXT,
        description TEXT,
        status enum ('PENDING','APPROVED','REJECTED','CANCELLED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table assembly_templates (
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        name varchar(200) not null,
        category varchar(255),
        description TEXT,
        template_content TEXT not null,
        variables TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table audit_logs (
        success bit not null,
        id bigint not null auto_increment,
        `timestamp` datetime(6) not null,
        action varchar(50) not null,
        ip_address varchar(50),
        entity_id varchar(100),
        entity_type varchar(100),
        username varchar(100),
        description varchar(500),
        user_agent varchar(500),
        error_message TEXT,
        new_value TEXT,
        old_value TEXT,
        user_id varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bar_assets (
        acquire_date date not null,
        asset_value decimal(19,2) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        name varchar(200) not null,
        remark varchar(500),
        status enum ('AVAILABLE','IN_USE','MAINTENANCE','RETIRED','DISPOSED') not null,
        type enum ('EQUIPMENT','FACILITY','VEHICLE','INVENTORY','LICENSE','OTHER') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bar_certificate_borrow_records (
        expected_return_date date,
        borrowed_at datetime(6) not null,
        certificate_id bigint not null,
        id bigint not null auto_increment,
        project_id bigint,
        returned_at datetime(6),
        borrower varchar(100) not null,
        purpose varchar(200),
        remark varchar(500),
        status enum ('BORROWED','RETURNED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bar_certificates (
        expected_return_date date,
        expiry_date date,
        bar_asset_id bigint not null,
        created_at datetime(6) not null,
        current_project_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6),
        current_borrower varchar(100),
        holder varchar(100),
        provider varchar(100),
        type varchar(100) not null,
        borrow_purpose varchar(200),
        location varchar(200),
        serial_no varchar(200) not null,
        remark varchar(500),
        status enum ('AVAILABLE','BORROWED','EXPIRED','DISABLED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bar_site_accounts (
        bar_asset_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        role varchar(30) not null,
        status varchar(30) not null,
        phone varchar(50) not null,
        owner varchar(100) not null,
        username varchar(100) not null,
        email varchar(200),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bar_site_attachments (
        bar_asset_id bigint not null,
        id bigint not null auto_increment,
        uploaded_at datetime(6) not null,
        size varchar(50),
        content_type varchar(100),
        uploaded_by varchar(100),
        name varchar(200) not null,
        url varchar(500),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bar_site_sops (
        bar_asset_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        estimated_time varchar(100),
        reset_url varchar(500),
        unlock_url varchar(500),
        contacts_json TEXT,
        faqs_json TEXT,
        history_json TEXT,
        required_docs_json TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bar_site_verifications (
        bar_asset_id bigint not null,
        id bigint not null auto_increment,
        verified_at datetime(6) not null,
        status varchar(30) not null,
        verified_by varchar(100) not null,
        message varchar(500),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bid_result_fetch_results (
        amount decimal(19,2),
        contract_duration_months integer,
        contract_end_date date,
        contract_start_date date,
        sku_count integer,
        analysis_document_id bigint,
        confirmed_at datetime(6),
        confirmed_by bigint,
        created_at datetime(6) not null,
        fetch_time datetime(6) not null,
        id bigint not null auto_increment,
        notice_document_id bigint,
        project_id bigint,
        tender_id bigint,
        updated_at datetime(6) not null,
        source varchar(200) not null,
        project_name varchar(500) not null,
        win_announce_doc_url varchar(500),
        remark varchar(2000),
        ignored_reason varchar(255),
        registration_type enum ('MANUAL','SYNC','FETCH'),
        result enum ('WON','LOST') not null,
        status enum ('PENDING','CONFIRMED','IGNORED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bid_result_reminders (
        attachment_document_id bigint,
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        last_result_id bigint,
        owner_id bigint,
        project_id bigint not null,
        remind_time datetime(6) not null,
        updated_at datetime(6) not null,
        uploaded_at datetime(6),
        uploaded_by bigint,
        last_reminder_comment varchar(500),
        project_name varchar(500) not null,
        created_by_name varchar(255) not null,
        owner_name varchar(255) not null,
        reminder_type enum ('NOTICE','REPORT') not null,
        status enum ('PENDING','REMINDED','UPLOADED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table bid_result_sync_logs (
        affected_count integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        operator_id bigint,
        source varchar(200) not null,
        message varchar(500),
        operator_name varchar(255) not null,
        operation_type enum ('SYNC','FETCH') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table business_qualifications (
        expected_return_date date,
        expiry_date date,
        issue_date date,
        reminder_days integer not null,
        reminder_enabled bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_reminded_at datetime(6),
        updated_at datetime(6) not null,
        current_project_id varchar(64),
        certificate_no varchar(120),
        current_borrower varchar(120),
        current_department varchar(120),
        holder_name varchar(120),
        issuer varchar(200),
        name varchar(200) not null,
        subject_name varchar(200) not null,
        file_url varchar(500),
        borrow_purpose varchar(255),
        category enum ('LICENSE','PRODUCT','PERSONNEL','OTHER') not null,
        current_borrow_status enum ('AVAILABLE','BORROWED','RETURNED') not null,
        status enum ('VALID','EXPIRING','EXPIRED') not null,
        subject_type enum ('COMPANY','SUBSIDIARY') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table calendar_events (
        event_date date not null,
        is_urgent bit,
        created_at datetime(6),
        id bigint not null auto_increment,
        project_id bigint,
        updated_at datetime(6),
        title varchar(500) not null,
        description TEXT,
        event_type enum ('DEADLINE','MEETING','MILESTONE','REMINDER','SUBMISSION','REVIEW') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_attachment_names (
        case_id bigint not null,
        attachment_name varchar(255)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_highlights (
        case_id bigint not null,
        highlight varchar(255)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_lessons_learned (
        case_id bigint not null,
        lesson_learned varchar(1000)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_reference_records (
        case_id bigint not null,
        id bigint not null auto_increment,
        referenced_at datetime(6) not null,
        referenced_by bigint,
        reference_context varchar(255),
        reference_target varchar(255) not null,
        referenced_by_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_share_records (
        case_id bigint not null,
        created_at datetime(6) not null,
        created_by bigint,
        expires_at datetime(6),
        id bigint not null auto_increment,
        created_by_name varchar(255) not null,
        token varchar(255) not null,
        url varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_success_factors (
        case_id bigint not null,
        success_factor varchar(1000)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_tags (
        case_id bigint not null,
        tag varchar(255)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table case_technologies (
        case_id bigint not null,
        technology varchar(255)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table cases (
        amount decimal(38,2) not null,
        project_date date,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        published_at datetime(6),
        source_project_id bigint,
        updated_at datetime(6),
        use_count bigint not null,
        view_count bigint not null,
        status varchar(30),
        visibility varchar(30),
        archive_summary TEXT,
        customer_name varchar(255),
        description TEXT,
        document_snapshot_text TEXT,
        location_name varchar(255),
        price_strategy TEXT,
        product_line varchar(255),
        project_period varchar(255),
        search_document TEXT,
        title varchar(255) not null,
        industry enum ('REAL_ESTATE','INFRASTRUCTURE','MANUFACTURING','ENERGY','TRANSPORTATION','ENVIRONMENTAL','OTHER') not null,
        outcome enum ('WON','LOST','IN_PROGRESS') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table collaboration_threads (
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        updated_at datetime(6),
        title varchar(500) not null,
        status enum ('OPEN','IN_PROGRESS','RESOLVED','CLOSED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table comments (
        is_deleted bit,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        parent_id bigint,
        thread_id bigint not null,
        updated_at datetime(6),
        user_id bigint not null,
        content TEXT not null,
        mentions varchar(255),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table competition_analyses (
        win_probability decimal(5,2),
        analysis_date datetime(6),
        competitor_id bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        competitive_advantage TEXT,
        recommended_strategy TEXT,
        risk_factors TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table competitor_win_records (
        amount decimal(19,2),
        sku_count integer,
        won_at date,
        competitor_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        project_id bigint,
        recorded_by bigint,
        discount varchar(100),
        recorded_name varchar(100),
        category varchar(200),
        competitor_name varchar(200) not null,
        payment_terms varchar(200),
        project_name varchar(500),
        notes varchar(2000),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table competitors (
        market_share decimal(5,2),
        typical_bid_range_max decimal(19,2),
        typical_bid_range_min decimal(19,2),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        industry varchar(100),
        name varchar(200) not null,
        strengths TEXT,
        weaknesses TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table compliance_check_results (
        risk_score integer not null,
        checked_at datetime(6) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        project_id bigint,
        tender_id bigint,
        updated_at datetime(6),
        checked_by varchar(100),
        check_details TEXT,
        overall_status enum ('COMPLIANT','NON_COMPLIANT','PARTIAL_COMPLIANT','WARNING') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table compliance_rules (
        enabled bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        name varchar(200) not null,
        description varchar(1000),
        rule_definition TEXT,
        rule_type enum ('QUALIFICATION','DOCUMENT','FINANCIAL','EXPERIENCE','DEADLINE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table customer_predictions (
        avg_budget decimal(14,2),
        confidence decimal(3,2),
        frequency integer,
        opportunity_score integer,
        predicted_budget_max decimal(14,2),
        predicted_budget_min decimal(14,2),
        converted_project_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_computed_at datetime(6),
        updated_at datetime(6),
        predicted_window varchar(20),
        cycle_type varchar(50),
        industry varchar(50),
        predicted_category varchar(50),
        purchaser_hash varchar(64) not null,
        period_months varchar(100),
        region varchar(100),
        sales_rep varchar(100),
        evidence_record_ids varchar(500),
        main_categories varchar(500),
        purchaser_name varchar(255) not null,
        reasoning_summary TEXT,
        status enum ('WATCH','RECOMMEND','CONVERTED','CANCELLED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table dimension_scores (
        score integer,
        weight decimal(38,2),
        analysis_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        dimension_name varchar(100) not null,
        comments TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_archive_records (
        archived_at datetime(6) not null,
        archived_by bigint,
        export_id bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        structure_id bigint not null,
        archive_reason varchar(255) not null,
        archived_by_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_assemblies (
        assembled_at datetime(6) not null,
        assembled_by bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        template_id bigint,
        assembled_content TEXT,
        variables TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_assignments (
        due_date date,
        assigned_by bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        project_id bigint not null,
        section_id bigint not null,
        updated_at datetime(6) not null,
        owner varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_export_files (
        export_id bigint not null,
        id bigint not null auto_increment,
        content TEXT not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_exports (
        exported_at datetime(6) not null,
        exported_by bigint,
        file_size bigint not null,
        id bigint not null auto_increment,
        project_id bigint not null,
        structure_id bigint not null,
        content_type varchar(255) not null,
        exported_by_name varchar(255) not null,
        file_name varchar(255) not null,
        format varchar(255) not null,
        project_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_locks (
        locked bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        locked_at datetime(6),
        locked_by bigint,
        project_id bigint not null,
        section_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_reminders (
        id bigint not null auto_increment,
        project_id bigint not null,
        reminded_at datetime(6) not null,
        reminded_by bigint,
        section_id bigint not null,
        message TEXT,
        recipient varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_sections (
        order_index integer,
        created_at datetime(6),
        id bigint not null auto_increment,
        parent_id bigint,
        structure_id bigint not null,
        updated_at datetime(6),
        content TEXT,
        metadata varchar(255),
        title varchar(255) not null,
        section_type enum ('CHAPTER','SECTION','SUBSECTION','TABLE','IMAGE','ATTACHMENT'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_structures (
        created_at datetime(6),
        id bigint not null auto_increment,
        project_id bigint not null,
        root_section_id bigint,
        updated_at datetime(6),
        name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table document_versions (
        is_current bit,
        version_number integer not null,
        created_at datetime(6),
        created_by bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        change_summary varchar(255),
        content TEXT,
        document_id varchar(255),
        file_path varchar(255),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table email_verification_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        verified_at datetime(6),
        token varchar(128) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table expense_approval_records (
        acted_at datetime(6) not null,
        expense_id bigint not null,
        id bigint not null auto_increment,
        approver varchar(100) not null,
        comment varchar(500),
        result enum ('APPROVED','REJECTED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table expense_payment_records (
        amount decimal(19,2) not null,
        created_at datetime(6) not null,
        expense_id bigint not null,
        id bigint not null auto_increment,
        paid_at datetime(6) not null,
        payment_method varchar(50),
        paid_by varchar(100) not null,
        payment_reference varchar(100),
        remark varchar(500),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table expenses (
        amount decimal(19,2) not null,
        date date not null,
        expected_return_date date,
        approved_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_return_reminder_at datetime(6),
        project_id bigint not null,
        return_confirmed_at datetime(6),
        return_requested_at datetime(6),
        updated_at datetime(6),
        approved_by varchar(100),
        created_by varchar(100) not null,
        expense_type varchar(100),
        approval_comment varchar(500),
        description varchar(500),
        return_comment varchar(500),
        category enum ('MATERIAL','LABOR','EQUIPMENT','TRANSPORTATION','SUBCONTRACTING','OVERHEAD','OTHER') not null,
        status enum ('PENDING_APPROVAL','APPROVED','REJECTED','PAID','RETURN_REQUESTED','RETURNED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table export_tasks (
        progress integer,
        completed_at datetime(6),
        created_at datetime(6) not null,
        created_by bigint not null,
        expires_at datetime(6),
        file_size bigint,
        id bigint not null auto_increment,
        data_type varchar(50) not null,
        file_path varchar(500),
        error_message TEXT,
        export_params TEXT,
        file_name varchar(255),
        export_type enum ('EXCEL','PDF') not null,
        status enum ('PENDING','PROCESSING','COMPLETED','FAILED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table fees (
        amount decimal(19,2) not null,
        created_at datetime(6) not null,
        fee_date datetime(6) not null,
        id bigint not null auto_increment,
        payment_date datetime(6),
        project_id bigint not null,
        return_date datetime(6),
        updated_at datetime(6),
        paid_by varchar(200),
        return_to varchar(200),
        remarks varchar(1000),
        fee_type enum ('BID_BOND','SERVICE_FEE','DOCUMENT_FEE','TRAVEL_FEE','NOTARY_FEE','OTHER_FEE') not null,
        status enum ('PENDING','PAID','RETURNED','CANCELLED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table historical_project_snapshots (
        archive_record_id bigint not null,
        captured_at datetime(6) not null,
        export_id bigint not null,
        id bigint not null auto_increment,
        project_id bigint not null,
        project_name varchar(500) not null,
        archive_summary TEXT not null,
        customer_name varchar(255),
        document_snapshot_text TEXT not null,
        product_line varchar(255),
        recommended_tags TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table password_reset_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        used_at datetime(6),
        user_id bigint not null,
        token varchar(128) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table platform_accounts (
        return_count integer,
        borrowed_at datetime(6),
        borrowed_by bigint,
        created_at datetime(6) not null,
        due_at datetime(6),
        id bigint not null auto_increment,
        updated_at datetime(6),
        username varchar(100) not null,
        account_name varchar(200) not null,
        password varchar(255) not null,
        platform_type enum ('GOV_PROCUREMENT','BIDDING_PLATFORM','CONSTRUCTION_PLATFORM','OTHER') not null,
        status enum ('AVAILABLE','IN_USE','MAINTENANCE','DISABLED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_documents (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        linked_entity_id bigint,
        project_id bigint not null,
        uploader_id bigint,
        file_url varchar(1000),
        document_category varchar(255),
        file_size varchar(255),
        file_type varchar(255),
        linked_entity_type varchar(255),
        name varchar(255) not null,
        uploader_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_group_members (
        project_group_id bigint not null,
        user_id bigint
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_group_projects (
        project_group_id bigint not null,
        project_id bigint
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_group_role_access (
        project_group_id bigint not null,
        role_code enum ('ADMIN','MANAGER','STAFF')
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_groups (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        manager_user_id bigint,
        updated_at datetime(6) not null,
        group_code varchar(100) not null,
        group_name varchar(200) not null,
        visibility enum ('ALL','MEMBERS','MANAGER','CUSTOM') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_quality_checks (
        `empty` bit not null,
        checked_at datetime(6) not null,
        document_id bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        document_name varchar(255),
        status varchar(255) not null,
        summary TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_quality_issues (
        adopted bit not null,
        ignored bit not null,
        check_id bigint not null,
        id bigint not null auto_increment,
        location_label varchar(255),
        original_text TEXT,
        suggestion_text TEXT,
        type varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_reminders (
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        remind_at datetime(6) not null,
        created_by_name varchar(255) not null,
        message TEXT,
        recipient varchar(255) not null,
        title varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_score_drafts (
        source_page integer,
        source_row_index integer not null,
        source_table_index integer not null,
        assignee_id bigint,
        created_at datetime(6) not null,
        due_date datetime(6),
        generated_task_id bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        updated_at datetime(6) not null,
        category varchar(50) not null,
        task_action varchar(50) not null,
        assignee_name varchar(100),
        score_value_text varchar(100),
        generated_task_description text not null,
        generated_task_title varchar(255) not null,
        score_item_title varchar(255) not null,
        score_rule_text text not null,
        skip_reason varchar(255),
        source_file_name varchar(255) not null,
        suggested_deliverables text not null,
        status enum ('DRAFT','READY','SKIPPED','GENERATED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_score_previews (
        budget decimal(19,2),
        win_score integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        project_id bigint,
        tender_id bigint,
        updated_at datetime(6),
        win_level varchar(20) not null,
        industry varchar(100),
        payload_json TEXT not null,
        project_name varchar(255),
        tags_json TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_share_links (
        created_at datetime(6) not null,
        created_by bigint,
        expires_at datetime(6),
        id bigint not null auto_increment,
        project_id bigint not null,
        created_by_name varchar(255) not null,
        token varchar(255) not null,
        url varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table project_team_members (
        member_id bigint,
        project_id bigint not null
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table projects (
        budget decimal(14,2),
        deadline date,
        created_at datetime(6) not null,
        end_date datetime(6),
        id bigint not null auto_increment,
        manager_id bigint not null,
        start_date datetime(6),
        tender_id bigint not null,
        updated_at datetime(6),
        industry varchar(50),
        customer_manager varchar(100),
        customer_manager_id varchar(100),
        region varchar(100),
        source_customer_id varchar(100),
        source_module varchar(100),
        source_opportunity_id varchar(100),
        name varchar(500) not null,
        tags_json varchar(1000),
        ai_analysis_json TEXT,
        competitor_analysis_json TEXT,
        customer varchar(255),
        description TEXT,
        platform varchar(255),
        remark TEXT,
        source_customer varchar(255),
        source_reasoning_summary TEXT,
        tasks_json TEXT,
        status enum ('INITIATED','PREPARING','REVIEWING','SEALING','BIDDING','ARCHIVED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table qualification_attachments (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        qualification_id bigint not null,
        uploaded_at datetime(6) not null,
        file_url varchar(500) not null,
        file_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table qualification_loan_records (
        expected_return_date date,
        borrowed_at datetime(6) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        qualification_id bigint not null,
        returned_at datetime(6),
        updated_at datetime(6) not null,
        project_id varchar(64),
        borrower varchar(120) not null,
        department varchar(120),
        remark varchar(500),
        return_remark varchar(500),
        purpose varchar(255),
        status enum ('AVAILABLE','BORROWED','RETURNED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table qualifications (
        expiry_date date,
        issue_date date,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        file_url varchar(255),
        name varchar(255) not null,
        level enum ('FIRST','SECOND','THIRD','OTHER') not null,
        type enum ('CONSTRUCTION','DESIGN','SERVICE','OTHER') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table refresh_sessions (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        last_seen_at datetime(6),
        revoked_at datetime(6),
        updated_at datetime(6) not null,
        user_id bigint not null,
        ip_address varchar(45),
        token_hash varchar(128) not null,
        user_agent varchar(500),
        device_info varchar(255),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table roi_analyses (
        estimated_cost decimal(19,2),
        estimated_profit decimal(19,2),
        estimated_revenue decimal(19,2),
        payback_period_months integer,
        roi_percentage decimal(10,2),
        analysis_date datetime(6),
        created_by bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        assumptions TEXT,
        risk_factors TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table roles (
        enabled bit not null,
        is_system bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6),
        data_scope varchar(32) not null,
        code varchar(64) not null,
        name varchar(100) not null,
        allowed_depts varchar(4000),
        allowed_projects varchar(4000),
        menu_permissions varchar(4000),
        description varchar(255),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table score_analyses (
        is_ai_generated bit,
        overall_score integer,
        analysis_date datetime(6),
        analyst_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        project_id bigint not null,
        updated_at datetime(6),
        summary TEXT,
        risk_level enum ('LOW','MEDIUM','HIGH'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table system_settings (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        config_key varchar(100) not null,
        payload_json TEXT not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table task_deliverables (
        version integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        task_id bigint not null,
        uploader_id bigint,
        file_size varchar(50),
        file_type varchar(100),
        uploader_name varchar(100) not null,
        storage_path varchar(500),
        name varchar(255) not null,
        storage_key varchar(255),
        deliverable_type enum ('DOCUMENT','QUALIFICATION','TECHNICAL','QUOTATION','OTHER') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table tasks (
        assignee_id bigint,
        created_at datetime(6) not null,
        due_date datetime(6),
        id bigint not null auto_increment,
        project_id bigint not null,
        updated_at datetime(6),
        assignee_role_code varchar(64),
        assignee_dept_code varchar(100),
        assignee_dept_name varchar(100),
        assignee_role_name varchar(100),
        description TEXT,
        title varchar(255) not null,
        priority enum ('LOW','MEDIUM','HIGH','URGENT') not null,
        status enum ('TODO','IN_PROGRESS','REVIEW','COMPLETED','CANCELLED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table template_download_records (
        downloaded_at datetime(6) not null,
        downloaded_by bigint,
        id bigint not null auto_increment,
        template_id bigint not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table template_tags (
        template_id bigint not null,
        tag varchar(255)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table template_use_records (
        id bigint not null auto_increment,
        project_id bigint,
        template_id bigint not null,
        used_at datetime(6) not null,
        used_by bigint,
        applied_options varchar(255),
        doc_type varchar(255) not null,
        document_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table template_versions (
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        template_id bigint not null,
        description varchar(1000),
        snapshot_name varchar(255) not null,
        version varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table templates (
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        updated_at datetime(6),
        description varchar(2000),
        current_version varchar(255),
        document_type varchar(255),
        file_size varchar(255),
        file_url varchar(255),
        industry varchar(255),
        name varchar(255) not null,
        product_type varchar(255),
        category enum ('TECHNICAL','COMMERCIAL','LEGAL','QUALIFICATION','CONTRACT','OTHER') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table tender_assignment_records (
        assigned_at datetime(6) not null,
        assigned_by_id bigint,
        assignee_id bigint not null,
        id bigint not null auto_increment,
        tender_id bigint not null,
        assigned_by_name varchar(255),
        assignee_name varchar(255) not null,
        remark TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table tenders (
        ai_score integer,
        budget decimal(19,2),
        created_at datetime(6) not null,
        deadline datetime(6),
        id bigint not null auto_increment,
        updated_at datetime(6),
        external_id varchar(100),
        source varchar(200),
        title varchar(500) not null,
        original_url varchar(1000),
        risk_level enum ('LOW','MEDIUM','HIGH'),
        status enum ('PENDING','TRACKING','BIDDED','ABANDONED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table users (
        email_verified bit not null,
        enabled bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        role_id bigint,
        updated_at datetime(6),
        phone varchar(32),
        department_code varchar(100),
        department_name varchar(100),
        email varchar(255) not null,
        full_name varchar(255) not null,
        password varchar(255) not null,
        username varchar(255) not null,
        role enum ('ADMIN','MANAGER','STAFF') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    alter table accounts
       add constraint UK_qtv290mh55xhggmpwosf5ag0v unique (name);

    create index idx_ai_job_target
       on ai_analysis_jobs (target_type, target_id);

    create index idx_ai_job_status
       on ai_analysis_jobs (status);

    create index idx_ai_result_tender
       on ai_analysis_results (tender_id, created_at);

    create index idx_ai_result_project
       on ai_analysis_results (project_id, created_at);

    create index idx_ai_result_job
       on ai_analysis_results (job_id);

    create index idx_approval_request_id
       on approval_actions (approval_request_id);

    create index idx_action_time
       on approval_actions (action_time);

    create index idx_actor_id
       on approval_actions (actor_id);

    create index idx_project_id
       on approval_requests (project_id);

    create index idx_status
       on approval_requests (status);

    create index idx_requester_id
       on approval_requests (requester_id);

    create index idx_created_at
       on approval_requests (created_at);

    create index idx_approval_type
       on approval_requests (approval_type);

    create index idx_template_category
       on assembly_templates (category);

    create index idx_template_created_by
       on assembly_templates (created_by);

    create index idx_audit_user
       on audit_logs (user_id);

    create index idx_audit_action
       on audit_logs (action);

    create index idx_audit_timestamp
       on audit_logs (`timestamp`);

    create index idx_audit_entity
       on audit_logs (entity_type, entity_id);

    alter table bar_site_sops
       add constraint UK_i8je16cbyau9qsiu4qoa5fx4d unique (bar_asset_id);

    create index idx_bid_result_fetch_status
       on bid_result_fetch_results (status);

    create index idx_bid_result_fetch_project
       on bid_result_fetch_results (project_id);

    create index idx_bid_result_fetch_tender
       on bid_result_fetch_results (tender_id);

    create index idx_bid_result_reminder_project
       on bid_result_reminders (project_id);

    create index idx_bid_result_reminder_status
       on bid_result_reminders (status);

    create index idx_bid_result_sync_type
       on bid_result_sync_logs (operation_type);

    create index idx_event_date
       on calendar_events (event_date);

    create index idx_event_type
       on calendar_events (event_type);

    create index idx_calendar_project_id
       on calendar_events (project_id);

    create index idx_urgent
       on calendar_events (is_urgent);

    create index idx_date_range
       on calendar_events (event_date, event_type);

    alter table case_share_records
       add constraint UK_p7mn0hbtj3xf60qa6uvdmvu7 unique (token);

    create index idx_thread_project
       on collaboration_threads (project_id);

    create index idx_thread_status
       on collaboration_threads (status);

    create index idx_thread_project_status
       on collaboration_threads (project_id, status);

    create index idx_comment_thread
       on comments (thread_id);

    create index idx_comment_user
       on comments (user_id);

    create index idx_comment_parent
       on comments (parent_id);

    create index idx_comment_deleted
       on comments (is_deleted);

    create index idx_comment_thread_deleted
       on comments (thread_id, is_deleted);

    create index idx_analysis_project
       on competition_analyses (project_id);

    create index idx_analysis_competitor
       on competition_analyses (competitor_id);

    create index idx_analysis_date
       on competition_analyses (analysis_date);

    create index idx_analysis_project_competitor
       on competition_analyses (project_id, competitor_id);

    create index idx_competitor_name
       on competitors (name);

    create index idx_competitor_industry
       on competitors (industry);

    create index idx_result_project
       on compliance_check_results (project_id);

    create index idx_result_tender
       on compliance_check_results (tender_id);

    create index idx_result_status
       on compliance_check_results (overall_status);

    create index idx_result_checked_at
       on compliance_check_results (checked_at);

    create index idx_rule_type
       on compliance_rules (rule_type);

    create index idx_rule_enabled
       on compliance_rules (enabled);

    create index idx_cp_purchaser_hash
       on customer_predictions (purchaser_hash);

    create index idx_cp_status
       on customer_predictions (status);

    create index idx_cp_opportunity_score
       on customer_predictions (opportunity_score);

    create index idx_dimension_analysis
       on dimension_scores (analysis_id);

    create index idx_dimension_name
       on dimension_scores (dimension_name);

    create index idx_document_archive_project
       on document_archive_records (project_id);

    create index idx_document_archive_structure
       on document_archive_records (structure_id);

    create index idx_assembly_project
       on document_assemblies (project_id);

    create index idx_assembly_template
       on document_assemblies (template_id);

    create index idx_assembly_project_template
       on document_assemblies (project_id, template_id);

    alter table document_assignments
       add constraint UK_px8r2avjc26q8jot3arnlm7jt unique (section_id);

    create index idx_document_export_file_export
       on document_export_files (export_id);

    alter table document_export_files
       add constraint UK_2yxrqfdw37mkbsjr8cp6sqek8 unique (export_id);

    create index idx_document_export_project
       on document_exports (project_id);

    create index idx_document_export_structure
       on document_exports (structure_id);

    alter table document_locks
       add constraint UK_qapdeuoy23y462v3ig6kyy9yb unique (section_id);

    create index idx_document_version_project_id
       on document_versions (project_id);

    create index idx_document_id
       on document_versions (document_id);

    create index idx_project_current
       on document_versions (project_id, is_current);

    create index idx_email_verify_token
       on email_verification_tokens (token);

    create index idx_email_verify_user
       on email_verification_tokens (user_id);

    alter table email_verification_tokens
       add constraint UK_ewmvysc7e9y6uy7og2c21axa9 unique (token);

    create index idx_export_user
       on export_tasks (created_by);

    create index idx_export_status
       on export_tasks (status);

    create index idx_fee_project
       on fees (project_id);

    create index idx_fee_status
       on fees (status);

    create index idx_fee_type
       on fees (fee_type);

    create index idx_fee_project_status
       on fees (project_id, status);

    create index idx_history_snapshot_project
       on historical_project_snapshots (project_id);

    create index idx_history_snapshot_archive
       on historical_project_snapshots (archive_record_id);

    create index idx_history_snapshot_export
       on historical_project_snapshots (export_id);

    alter table historical_project_snapshots
       add constraint UK_c3q79d6kv4gwat3my93qyaewn unique (archive_record_id);

    create index idx_password_reset_token
       on password_reset_tokens (token);

    create index idx_password_reset_user
       on password_reset_tokens (user_id);

    create index idx_password_reset_expires
       on password_reset_tokens (expires_at);

    alter table password_reset_tokens
       add constraint UK_71lqwbwtklmljk3qlsugr1mig unique (token);

    create index idx_platform_username
       on platform_accounts (username);

    create index idx_platform_status
       on platform_accounts (status);

    create index idx_platform_type
       on platform_accounts (platform_type);

    create index idx_platform_borrowed_by
       on platform_accounts (borrowed_by);

    alter table platform_accounts
       add constraint UK_14e3v80s7wywpcjk713rniikd unique (username);

    create index idx_project_group_manager
       on project_groups (manager_user_id);

    alter table project_groups
       add constraint UK_n1sq2ky02kvteuxw8r5oo9q7e unique (group_code);

    create index idx_score_preview_project
       on project_score_previews (project_id, created_at);

    create index idx_score_preview_tender
       on project_score_previews (tender_id, created_at);

    alter table project_share_links
       add constraint UK_b8ltlmqyet5wpswegfyihvluk unique (token);

    create index idx_project_status
       on projects (status);

    create index idx_project_manager
       on projects (manager_id);

    create index idx_project_tender
       on projects (tender_id);

    create index idx_project_dates
       on projects (start_date, end_date);

    alter table refresh_sessions
       add constraint UK_8dm8ib9n3bwiy2fjx1411w08d unique (token_hash);

    create index idx_roi_project
       on roi_analyses (project_id);

    create index idx_roi_analysis_date
       on roi_analyses (analysis_date);

    alter table roles
       add constraint UK_ch1113horj4qr56f91omojv8 unique (code);

    create index idx_score_analysis_project
       on score_analyses (project_id);

    create index idx_score_analysis_date
       on score_analyses (analysis_date);

    create index idx_score_analysis_risk
       on score_analyses (risk_level);

    alter table system_settings
       add constraint UK_cv6i1lu658ukkwnk4cuvj0ow0 unique (config_key);

    create index idx_task_del_task_id
       on task_deliverables (task_id);

    create index idx_task_del_type
       on task_deliverables (deliverable_type);

    alter table task_deliverables
       add constraint UK_th3pfdl5atat5ggp5w92f03x unique (storage_key);

    create index idx_tender_status
       on tenders (status);

    create index idx_tender_source
       on tenders (source);

    create index idx_tender_deadline
       on tenders (deadline);

    create index idx_tender_ai_score
       on tenders (ai_score);

    alter table tenders
       add constraint UK_i07ipdk97nopwineqty0816ml unique (external_id);

    alter table users
       add constraint UK_6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table users
       add constraint UK_r43af9ap4edm43mmtq01oddj6 unique (username);

    alter table case_attachment_names
       add constraint FKei1fv1mtwa9xb5ufcqhty2ix4
       foreign key (case_id)
       references cases (id);

    alter table case_highlights
       add constraint FKbv3me1wdrtmxs3q3kt94flu0k
       foreign key (case_id)
       references cases (id);

    alter table case_lessons_learned
       add constraint FK86g2td5ti22l2yhsomuj0huuv
       foreign key (case_id)
       references cases (id);

    alter table case_success_factors
       add constraint FKhc1yltykgesh29adh8c1rveox
       foreign key (case_id)
       references cases (id);

    alter table case_tags
       add constraint FK5lux2odxtf0cmef0shrqakd07
       foreign key (case_id)
       references cases (id);

    alter table case_technologies
       add constraint FKsgesjrn67pmbsbjww06appe3g
       foreign key (case_id)
       references cases (id);

    alter table email_verification_tokens
       add constraint FKi1c4mmamlb8keqt74k4lrtwhc
       foreign key (user_id)
       references users (id);

    alter table password_reset_tokens
       add constraint FKk3ndxg5xp6v7wd4gjyusp15gq
       foreign key (user_id)
       references users (id);

    alter table project_group_members
       add constraint FKr0fmvpgokjn2mpptv7qat6shh
       foreign key (project_group_id)
       references project_groups (id);

    alter table project_group_projects
       add constraint FKq6hhbhmbsib75deebi5e5tth4
       foreign key (project_group_id)
       references project_groups (id);

    alter table project_group_role_access
       add constraint FKao9yo4xloylqq0iphrna0rm03
       foreign key (project_group_id)
       references project_groups (id);

    alter table project_team_members
       add constraint FKrplt19ljycvlrk9fy72yep75e
       foreign key (project_id)
       references projects (id);

    alter table refresh_sessions
       add constraint FK9ndfh1op1iy3xuqyi6gxkseg0
       foreign key (user_id)
       references users (id);

    alter table template_download_records
       add constraint FK6hyrt9u60k493mra3vqtg13b5
       foreign key (template_id)
       references templates (id);

    alter table template_tags
       add constraint FKb690bd7lptenw09h0u23iwila
       foreign key (template_id)
       references templates (id);

    alter table template_use_records
       add constraint FK3ia252hoqeioht3vqvxqgc05s
       foreign key (template_id)
       references templates (id);

    alter table template_versions
       add constraint FK9gy13bl9n6n45605pb93htaxx
       foreign key (template_id)
       references templates (id);

    alter table users
       add constraint FKp56c1712k691lhsyewcssf40f
       foreign key (role_id)
       references roles (id);

    insert into roles (code, name, description, is_system, enabled, data_scope, menu_permissions, created_at, updated_at)
    values
       ('admin', '管理员', '系统管理员，拥有所有权限', true, true, 'all', 'all', current_timestamp(6), current_timestamp(6)),
       ('manager', '经理', '部门经理，可查看项目、知识库、资源与分析数据', true, true, 'dept', 'dashboard,bidding,project,knowledge,resource,analytics,settings', current_timestamp(6), current_timestamp(6)),
       ('staff', '员工', '业务人员，可查看工作台、标讯、项目、知识库与资源', true, true, 'self', 'dashboard,bidding,project,knowledge,resource', current_timestamp(6), current_timestamp(6));
