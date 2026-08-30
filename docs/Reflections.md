## Reflection

### Customer Interface Workflow

I began by setting project-wide context and engineering standards before asking Codex to implement features. I gave Codex access to a `/Resource` folder containing CS2113 textbook and Java coding standards, and I provided high-level product boundaries such as expected engineering behaviour, agent workflow, and areas that required human control. From this context, I asked Codex to generate an AGENTS.md file so that the development rules and standards would persist across later tasks.

For the customer interface, however, my interaction with Codex was largely directive rather than collaborative. I would decide on a feature, describe the intended behaviour, answer a few clarification questions, and then allow Codex to implement it. This worked reasonably well for straightforward tasks, but it exposed a major weakness. Codex tends to treat my prompt as authoritative rather than challenge incomplete product assumptions.

For example, when I asked Codex to implement the snack menu and pricing system, my initial specification only described selecting a snack option. I failed to specify that customers should be able to select multiple different snacks and specify quantities. Codex implemented the narrower interpretation without questioning whether that behaviour made sense for a food-ordering workflow. From a business-logical perspective, this was clearly incomplete because a customer would reasonably expect to purchase more than one type of snack and more than one unit.

This showed that Codex was optimising for instruction compliance rather than product completeness. It did not infer that my requirement was probably underspecified, nor did it challenge me before implementation. I had to identify the issue manually and provide another prompt to expand the feature.

A similar issue occurred during the promo-code feature. I asked Codex to support different discount codes, but Codex went beyond the requested scope and generated a bill summary before I had specified what the bill should contain. The resulting summary omitted important movie and screening information such as the selected movie and screening time. In this case, Codex made the opposite mistake. Instead of being too literal, it filled in an unspecified product decision itself but did so without sufficient business context.

These two cases revealed an important limitation of my original prompting style. Codex could be both too obedient when requirements were incomplete and too assumptive when it decided to extend the feature beyond the stated request. In both cases, the underlying problem was the same. The agent did not possess the business intent that I had not explicitly captured.

### How did my prompt evolve?

For the customer interface, I relied mainly on context prompting and tried to provide increasingly detailed implementation instructions. Over time, I realised that this approach made my knowledge the bottleneck. If I failed to notice a requirement, Codex often failed to surface it as well. Giving more detailed prompts improved execution accuracy, but it did not necessarily improve the quality of the decisions being made.

I therefore changed the relationship I wanted with Codex for the administrator interface. Instead of treating the agent primarily as an executor, I wanted it to act as a planning partner that would actively expose unclear requirements before implementation.

To do this, I installed planning skills from Matt Pocock, particularly the grilling/grill-me workflow. Before implementing each major administrator feature, Codex would first interrogate the feature requirements through a structured series of questions. These sessions often involved around 20 questions and forced decisions about edge cases, validation, persistence, failure behaviour, and feature boundaries.

After the requirements were resolved, I asked Codex to generate a Product Requirements Document (PRD) describing the intended observable behaviour. For Movie Management, the PRD defined the functional requirements, non-functional requirements, use cases, acceptance criteria, and explicit scope boundaries.

I then asked Codex to create a Technical Design Document, which translated those approved product requirements into implementation architecture, persistence behaviour, testing seams, transaction design, and a test-driven implementation sequence. Finally, Codex produced a requirements-to-tests mapping so that each requirement could be traced to verification evidence.

This represented a major shift from my original workflow. Instead of trying to make the implementation prompt increasingly detailed, I moved important decisions into explicit planning artifacts before implementation began.

I also began starting a fresh Codex conversation for each major feature. This has two practical advantages. First, it reduced context accumulation. Instead of keeping one increasingly long conversation alive throughout the project, each feature started with only the artifacts and repository state relevant to that task. This reduced the likelihood that outdated or unrelated conversational details would influence later work.

Second, the PRD and TDD acted as externalised project memory. A new conversation could begin by reading the approved artifacts and current repository rather than depending on Codex to retain decisions from earlier chats. This made the workflow more reproducible and made feature boundaries easier to preserve.

However, this came at a cost. Creating and reviewing these artifacts consumed significant time and model usage before implementation began. More importantly, an artifact only improves governance if I understand and challenge the decisions recorded in it. In several cases, I approved Codex-generated decisions because they appeared reasonable without fully understanding their downstream implementation complexity.

### From prompting for implementation to engineering the workflow

The most important change was therefore not that my prompts became longer. Instead, I began changing what the prompt was responsible for. Earlier prompts attempted to describe the feature sufficiently well for Codex to implement it. Later prompts defined a process within which Codex had to work: inspect existing constraints, expose unresolved decisions, produce reviewable artifacts, stop at approval boundaries, implement only approved scope, and provide verification evidence.

This distinction became important because increasing prompt detail alone does not eliminate incorrect assumptions. A detailed prompt can still encode the wrong product decision, omit an important invariant, or accidentally authorise unnecessary architecture. What improved reliability was introducing explicit decision and verification fates around the model.

This closely resembles the course's idea of treating artifacts as interface between human and agent. The lecture describes a Mission Brief as a bounded intent that prevents gap-filling by invention, a Mentorship Pack as durable project rules, and a Workflow Runbook as a process with gates and checkpoints. In my project, `AGENTS.md`, the administrator plan, PRDs, TDDs, and requirements-to-tests mappings gradually began serving similar purposes.

### Example 1: Snack and combo selection

One of my earlier prompts was essentially a direct feature request: add a simple list of snacks and combos with prices after seat selection. I formulated the prompt this way because I considered the feature straightforward and expected the obvious behaviour of a food-ordering interface to be inferred. I was primarily thinking about where the menu belonged in the customer workflow rather than specifying the complete interaction model.

This assumption was a mistake. The prompt did not specify quantities, whether multiple menu items could be selected, how repeated selections should behave, how customers finished ordering, or whether the order needed persistence. Instead of stopping to surface these ambiguities, Codex selected its own bounded interpretation. It created a fixed menu and allowed the customer to select exactly one item or enter `0` to skip it.

The implementation was technical coherent and its test passed, but it did not satisfy the product behaviour I wanted. I only noticed the problem after looking at the customer interaction as a user: buying exactly one popcorn or one drink was an unreasonable restriction for a cinema ordering workflow. I then issued a follow-up prompt explicitly requiring quantities and allowing multiple different snacks and combos. This changed the implementation from a single selection into an order-building loop.

What was interesting was that verification of the implementation did not reveal the missing requirement. The initial version passed 69 JUnit tests. After the revised prompt, the feature passed 79 tests and a packaged-JAR smoke test covering multiple products, different quantities and replacement of a previous quantity. The test proved that Codex had implemented its interpretation correctly. They could not prove that the interpretation itself represented what I wanted.

This changed how I think about AI-assisted testing. Automated verification is only as good as the requirements against which the software is test. A model can generate a comprehensive test suite for an incomplete specification and produce very strong evidence that the wrong product was implemented correctly.

If I were writing the prompt again, I would first ask Codex to list product assumptions that materially affect observable behaviour and stop for approval before implementing them. For a small feature such as this, I would not create a full PRD and TDD. A short acceptance-criteria and assumption-review step would probably have prevented the rework at much lower cost.

### Example 2: Separating requirements from implementation

Movie Management was the first feature where I deliberately changed the prompt from "implement this" to a staged planning workflow. I instructed Codex to work only on Movie Management, read the existing administrator plan as a constraint, identify unresolved requirements and design decisions, and make no implementation changes. Implementation would only be authorised after the requirements, and technical design has been reviewed.

The reason for formulating the prompt this way was my experience with the customer interface. I no longer wanted implementation to be the mechanism through which unclear requirements were discovered. By the time Java code existed, changing a product assumption could require modifications to models, tests, documentation and persistence behaviour. I therefore moved uncertainty resolution earlier in the process.

The resulting workflow produced a Movie Management PRD, TDD and requirements-to-tests mapping before implementation. Codex later proposed five architectural extractions, including shared administrator input rules, presentation rendering, a generic terminal seam and a catalogue recovery gate. I still had to approve those changes rather than treating the design document as automatically correct.

A particularly useful lesson came from verification. After the refactor, all 184 tests passed, but the build still failed the repository's 100% coverage gate because newly introduced internal modules contained uncovered code. Codex had to revise the implementation and rerun verification before both line and branch coverage returned to 100%. This demonstrated that "tests passed" was not a sufficient completion signal.

More importantly, later document review showed that even apparently rigorous artifacts could contain unsupported claims. The Movie TDD review found that an earlier requirements-to-tests checklist claimed that non-deferred checks had passed even though several tests used weak substring assertions or omitted required state assertions. Eight verification gaps were reopened.

This made me realise that specification-driven development does not remove the need for human review. It changes the object being reviewed. Instead of reviewing only generated Java, I was now reviewing requirements, architecture, traceability and test evidence as well. A polished PRD or TDD can create a false sense of confidence if I approve it without understanding the consequences of its decisions.

### Example 3: Explicit contracts for multi-agent integration

The final role-routing and deferred-confirmation workstream used a much more constrained prompt than my early customer features. Instead of describing only the desired output, I explicitly froze responsibility boundaries: `Main` was to remain a thin bootstrap, `ApplicationRouter` would own role transitions and shared wiring, and `AdministratorApplication` would own only the administrator homepage and delegation. I also assigned separate areas of responsibility to multiple agents and required the final agent to perform integration, regression testing and audit.

The most important part of this prompt, however, was the customer transaction order. Earlier in development, seat occupancy was persisted before ticket, snack and promotion selection had completed. The billing interaction log records that abandoning the later customer workflow could therefore leave a seat permanently occupied even though the purchase had never completed.

This was not primarily a Java syntax problem. It required engineering judgement about what constituted a successful transaction. I eventually defined the invariant explicitly: validate all selections, construct the complete bill in memory, atomically persist the selected seats, display the bill only after persistence succeeds, and then continue to the post-session prompt. A failure before persistence must not display purchase success, while an output failure after persistence must not roll back already durable seat state.

This is an example where making the prompt more precise was necessary because the LLM could not infer the business transaction boundary safely. The correct ordering depended on the semantics of "purchase success," not simply on local code structure.

The multi-agent implementation also exposed another limitation. Although each part worked locally, the post-integration audit found that recovery was being invoked both by the new `ApplicationRouter` and independently inside the existing Movie and Screening applications. Each local implementation was plausible, but together they violated the intended single-owner architecture. Codex removed the duplicated recovery ownership only after integration review.

Again, the first integrated verification was not sufficient. All 286 tests passed, yet the JaCoCo gate still failed. Additional tests were required before the final isolated verification passed 301 tests with no missed lines or branches, followed by a packaged-JAR smoke test of the actual role-routing flow.

The lesson I took from this was that multi-agent parallelism does not eliminate integration risk; it can increase it. Agents can implement their individual contracts correctly while duplicating ownership or violating a global invariant when their outputs are combined. For multi-agent work, explicit ownership boundaries and a dedicated integration review were more important than simply dividing the work into independent tasks.

### Verification changed from "does it compile" to layered evidence

My verification strategy also evolved throughout the project. Early on, successful Maven tests gave me substantial confidence because most features were isolated and relatively small. As the system grew, I found that a green test suite was only one form of evidence.

The administrator test foundation made this especially visible. When the 100% JaCoCo gate was first introduced, the existing tests passed but coverage was only 92% for lines and 88% for branches. Closing those gaps required tests for malformed data, EOF, reader failures, storage failures, interruption, cleanup behaviour and boundary cases. The final foundation reached 130 passing tests with complete line and branch coverage.

However, I also learned not to treat 100% coverage as proof of correctness. Coverage demonstrates that code paths were executed, not that the right requirements were implemented or that assertions were meaningful. The snack feature passed its tests despite an incomplete product model, while the Movie TDD review found cases where passing tests did not provide the evidence that the traceability document claimed.

I therefore ended up relying on several layers of verification: focused unit and integration tests, requirements-to-tests traceability, coverage inspection, diff and standards checks, packaged-JAR smoke tests, and finally human inspection of the observable CLI workflow. These different methods caught different classes of problems.

### When prompting was less effective than direct inspection

There were also situations where another round of prompting was less useful than directly interacting with the program. CLI usability was one example. In Seat Selection, I had to correct the orientation of the seat map so that the number axis appeared below row A, furthest from the screen. This was easier to judge by looking at the rendered terminal interface than by attempting to specify every spatial detail in advance.

The same applied to the bill summary. The initial bill implementation was arithmetically correct but did not communicate the information in the way I wanted. I later supplied an explicit four-section receipt structure containing movie and screening information, tickets, snacks, promotion and totals. Exact-output tests were then useful for preserving the chosen design, but the initial design decision itself came from human judgement about readability rather than from automated verification.

I found that prompting was very effective when the desired behaviour could be stated as contracts, invariants or testable acceptance criteria. It was less effective when the unresolved question was essentially "does this interaction make sense to a user?" In those cases, running the program, inspecting the output and giving a small, targeted correction was often faster than asking the model to repeatedly critique its own UI.

### Engineering judgment remained the human responsibility

Codex substantially reduced the amount of mechanical implementation work I had to perform, but it did not remove the need for software engineering judgement. The decisions with the greatest consequences were generally not decisions about Java syntax. They were decisions about system boundaries and invariants.

For example, I had to decide when a seat became durably occupied, what state must survive different failure points, which component owned role transitions, whether rescheduling a screening should preserve occupancy, which persistence formats could change, what should be transactional, and when a proposed abstraction was too broad for the feature being implemented. Codex could present alternatives and trade-offs, but these choices depended on the meaning of the application rather than information available from the code alone.

I also had to judge when **not** to accept Codex's advice. One small example occurred when Codex incorrectly stated that Screening Management should be the next workstream; after checking the approved plan it corrected this to Pricing Storage. This reinforced the need for an external source of truth rather than relying on conversational memory.

This mirrors a limitation discussed in the course: AI-based review can still struggle with complex business logic and architecture decisions, which remain areas where human judgement is required.

### What I would do differently

If I repeated the project, I would keep the artifact-driven approach but make it more proportional to feature risk.

For every feature, before implementation, I would require Codex to distinguish three things: explicit requirements, assumptions it believes are safe to make, and decisions that require owner approval. Any assumption that changes user-visible behaviour, persistence, architecture or failure semantics should become a question rather than silently becoming code.

For small and reversible features, I would use a lightweight process: scope, assumptions, acceptance criteria, implementation and verification. The Snack feature did not need a large PRD/TDD cycle; it needed five minutes of product clarification before coding.

For features involving persistence, destructive operations, transactions or cross-feature architecture, I would retain the stricter workflow: requirement exploration, approved PRD, approved TDD, requirements-to-tests mapping, implementation, independent review and packaged-system verification. The Movie Management and role-routing work justified this additional cost because mistakes could affect durable state or multiple subsystems.

I would also separate product verification from implementation verification. Tests, coverage and static checks answer whether the implementation behaves according to the encoded specification. They do not answer whether the encoded specification is the product I intended. I would therefore include a short manual acceptance pass for each user-facing feature before considering it complete.

Finally, I would continue using fresh conversations and persistent repository artifacts, but I would be more critical about the amount of generated documentation. The PRD/TDD workflow improved reproducibility and reduced context dependence, but it also consumed significant time and model usage. The goal should not be to maximize documentation; it should be to create the minimum set of artifacts necessary to make important decisions explicit, reviewable and reproducible.

### Conclusion

My experience with Codex changed my view of AI-assisted software engineering. At the beginning, I treated the model primarily as a faster programmer: I supplied a feature description and evaluated the resulting code. By the end, the more valuable skill was not writing increasingly elaborate implementation prompts, but designing an environment in which the agent could work safely.

The project showed me that LLMs are extremely effective at implementation, testing, refactoring and documentation once the problem has been bounded. They are much less reliable at identifying unstated business intent or deciding which assumptions they should be allowed to make. The most serious risks I encountered were therefore not obvious hallucinations; they were reasonable-looking implementations of incomplete requirements, locally correct decisions that conflicted globally, and polished artifacts whose evidence still needed scrutiny.

My role increasingly became one of defining intent, approving trade-offs, protecting invariants, reviewing evidence and deciding when the agent should stop and ask. This is the main lesson I would carry into future AI-assisted development: reliable agentic software engineering depends less on trusting the model to make good decisions and more on engineering a process in which important decisions cannot be made silently.
