# Documentation Index

- English manual: [en-us/README.md](en-us/README.md)
- Japanese manual: [ja-jp/README.md](ja-jp/README.md)
- API draft: [API.md](API.md)
- Companion Mod requirements: [companion_mods/REQUIREMENTS.md](companion_mods/REQUIREMENTS.md)
- Companion Mod agent guide: [companion_mods/AGENTS.md](companion_mods/AGENTS.md)

## Docs Policy

Use locale directories (`en-us`, `ja-jp`) for end-user and operator manuals.

Keep selected root markdown files as canonical project-level documents shared across locales.

| File | Role | Localization policy |
|---|---|---|
| REQUIREMENTS.md | Product and engineering source-of-truth | Keep canonical at root |
| API.md | Public API contract draft | Keep canonical at root, link from locale docs |

If a root document requires language-specific explanation, create an explanatory companion in each locale directory and link back to the canonical root file.

## Current Implementation Notes

- Command UX uses canonical subcommands with alias normalization.
	- `gui -> menu`, `remove/destroy -> delete`, `gif -> give-frame`
- Tab completion and help prioritize canonical subcommands.
- Message localization is YAML-based (`messages_*.yml`) with default language `en` and optional `ja`.
