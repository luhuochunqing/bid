# Product Guidelines

## 1. Design & UX Principles
- **Clarity & Efficiency:** Optimize for high information density without visual clutter. Users should be able to parse complex tables and dashboards quickly.
- **Consistency:** Use consistent terminology, layout structures, and interaction patterns across all modules.
- **Accessibility:** Ensure the platform is accessible to all users, adhering to standard contrast ratios, providing clear error states, and supporting keyboard navigation.
- **Responsive Design:** While primarily a desktop web application, ensure core views (like dashboards and basic approvals) are usable on tablets and larger mobile devices.

## 2. Branding & Styling
- **Color Palette:** Professional and trustworthy. Primary color should convey reliability (e.g., enterprise blue), with clear semantic colors for status (green for success, red for errors, yellow/orange for warnings).
- **Typography:** Clean, legible sans-serif fonts suitable for data-heavy applications.
- **Components:** Leverage Element Plus for a cohesive, mature enterprise aesthetic, customizing the theme variables to match the brand.

## 3. Prose & Terminology
- **Tone:** Professional, objective, and instructional.
- **Clarity:** Avoid jargon where possible. When industry-specific terms (like "Bid Bond", "Tender", "ROI") are used, ensure they are used consistently.
- **Feedback Messages:** Error and success messages must be actionable. Tell the user exactly what happened and what they need to do next (e.g., "Failed to save: The 'Budget' field must be greater than zero." instead of "Error 500").

## 4. Interaction Patterns
- **Destructive Actions:** Require explicit confirmation for irreversible actions (e.g., deleting a project, submitting a final bid).
- **Data Entry:** Provide auto-save where feasible or clear "Save" vs "Cancel" patterns. Use sensible defaults and input validation as the user types.
- **Navigation:** Deeply nested modules must have clear breadcrumbs to help users understand their context within the platform.