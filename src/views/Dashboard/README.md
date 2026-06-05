# Dashboard Workbench

This folder keeps the Workbench page split by responsibility.

- `Workbench.vue` is the page shell. It wires stores, router, lifecycle loading, and child components.
- `components/WorkbenchStaticLayout.vue` keeps the fallback static Workbench layout presentational.
- `workbench-core.js` contains pure functions only: formatting, DTO mapping, role rules, and route targets.
- Workbench project lists show visible non-archived projects first, prioritizing current-owner and urgent/high-priority items instead of hiding visible projects by strict display-name matching.
- `workbench-quick-start-core.js` contains pure permission checks, form validation, and payload builders for the one-stop quick-start flows.
- `useWorkbench*.js` files are application-service composables. They perform API orchestration and state writes.
- `useWorkbenchDerivedLists.js` and `useWorkbenchDynamicWidgets.js` keep Workbench derivation and dynamic layout wiring out of the page shell.
- `useWorkbenchTodos.js` only loads system alert todos for admin/manager roles because alert history reads are protected by backend role authorization.
- `components/` contains display components. They receive props and emit events, and do not access APIs, stores, or router.
- `components/WelcomeBanner.vue` keeps banner action text inside `.banner-action-label`; local banner CSS must preserve readable contrast against global Element Plus primary button overrides.
- `components/WorkbenchQuickStart.vue` is the Workbench quick-start surface for bid support, qualification/contract borrow, and bid expense requests. Its side effects stay in `useWorkbenchQuickStart.js`.
- `styles/` contains Workbench CSS split into small files and imported by `styles/workbench-styles.js` so Vite can bundle them without CSS `@import` waterfalls.
- `MetricCards.vue` renders responsive KPI cards; the grid must adapt to the sidebar-constrained dashboard width without wrapping labels, values, or comparison text into vertical fragments.
- Empty/error states stay presentational; API composables expose state and the page shell wires retry actions. Icons are optional and should be omitted in dense calendar panels when the copy is already clear.
- Workbench brand accents should use the Xiyu Logo Green token from `DESIGN.md` for primary visual emphasis, including banner accents, selected navigation, and compact progress bars.
- Clickable cards must be keyboard reachable with visible focus states before shipping new interactions.

Keep every new source file under 300 lines. If a file approaches that limit, split by behavior before adding more code.
