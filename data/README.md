# Application Data

Mutable application state will be stored under `data/runtime/` using structured
UTF-8 plain-text files. Runtime files are ignored by Git.

The file format, schema, validation rules, update strategy, and any committed
sample-data policy are intentionally deferred until their requirements are
approved. Do not store credentials or other secrets here.

