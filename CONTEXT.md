# Lynxus Context

Lynxus is a compile-time Java ORM. It compiles Mapper declarations into explicit JDBC execution plans and generated implementations. This glossary defines the project-specific language used to describe product identity, compilation, execution ownership, and terminal outcomes.

## Language

**Compile-time Java ORM**:
Lynxus's product identity. Object-relational mapping is compiled from Mapper declarations into ordinary Java and executed through JDBC.
_Avoid_: mapper tool, SQL Mapper as the product name, lightweight mapper, Hibernate alternative, JPA replacement

**Mapper**:
The authoring unit of this ORM: one Java interface plus optional XML, compiled into one generated implementation bound to one DataSource domain. Mapper is not the product name.
_Avoid_: mapper tool, using Mapper as the product name

**MyBatis compatibility baseline**:
MyBatis 3.5.x deterministic mapping behavior is the comparison and migration baseline. Hibernate and JPA are not the competitive target.
_Avoid_: Hibernate comparison, JPA feature matrix as the product story

**Runtime interpretation**:
Evaluating Mapper XML, OGNL, or equivalent scripts while a Mapper method runs. Lynxus never does this; javac compiles supported declarations into ordinary Java. Runtime XML and OGNL are not missing features and are not backlog items.
_Avoid_: treating runtime XML or OGNL as a gap, MyBatis plugin chain as a Lynxus extension

**Typed extension**:
A user-supplied interface implementation bound at compile time or executor assembly and invoked by generated code or the executor. A feature can exist this way without being fully generated and without runtime XML or OGNL.
_Avoid_: MyBatis plugin, general SQL-rewrite chain, treating compile-time and feature as the same decision

**Execution plugin chain**:
The ordered adapters around `SqlExecutor` that own executor-level policy. Innermost adapter is `JdbcSqlExecutor`. Effects are observation, replacing an immutable plan, and short-circuiting with a result.
_Avoid_: JDBC phase chain, MyBatis `proceed()` on statement handlers, putting cache or pagination inside `JdbcSqlExecutor`

**Mapper SQL method**:
A Mapper method backed by a Lynxus SQL annotation, Mapper XML statement, or typed SQL provider. Two effective Mapper SQL methods with the same name are overloaded even when their parameter types differ.
_Avoid_: Statement method, query method

**Execution outcome**:
The terminal observation of one SQL executor invocation after all execution-owned JDBC resources have been released. It does not claim that a surrounding standalone or hosted transaction has committed or rolled back.
_Avoid_: Transaction result, commit result

**Final failure**:
The same throwable observed by terminal interceptors and delivered to the Mapper caller after execution cleanup. Its cause and suppressed failures preserve the underlying execution and cleanup evidence.
_Avoid_: Logged failure, parser failure

**Execution cleanup**:
The release of the result set, statement, and connection handle owned by one SQL executor invocation. Transaction commit and rollback are outside this boundary.
_Avoid_: Transaction completion, connection shutdown

**Transaction completion**:
The commit or rollback performed by the standalone transactional executor or a host transaction manager after participating SQL executions finish. It is distinct from an execution outcome.
_Avoid_: Execution cleanup, Mapper completion

**Terminal interceptor**:
An observational callback invoked after an execution outcome is final. A terminal interceptor does not own JDBC cleanup or transaction completion and cannot change an ordinary execution result through a runtime callback failure.
_Avoid_: Execution phase, transaction callback

**Compiler rejection**:
A deterministic javac error produced when Lynxus cannot safely compile a Mapper declaration. Compiler rejection never falls back to runtime SQL interpretation or guessed generated behavior.
_Avoid_: Parser warning, runtime fallback

**Diagnostic context**:
The Mapper, method, resource path, statement identifier, and any XML declaration details that Lynxus can determine without guessing. Context derived from the Mapper is distinct from declarations successfully parsed from XML.
_Avoid_: Parser stack trace, inferred XML declaration

**Offline XML resolution**:
XML resolution that permits only Lynxus-owned local resources and rejects external entities, XInclude, filesystem resources, and network resources.
_Avoid_: Best-effort XML parsing, remote DTD resolution

**Type handler manager**:
The Core runtime component that routes supported values from generated Java type information and live JDBC metadata. For results it resolves one typed handler per column before row iteration; that handler reads and converts every row value to its generated Java target type. Its standard routes are fixed and it does not construct result objects or discover user handlers.
_Avoid_: JDBC value adapter, row mapper, global type-handler registry

**Result mapping**:
A query-level declaration that maps result columns to one scalar, record constructor, or JavaBean property structure. Annotation and XML forms normalize into the same compilation model.
_Avoid_: Type handler, entity column mapping, row mapper

**Result assembler**:
A compile-time-generated strategy carried by a typed query execution plan. It constructs one scalar, record, or JavaBean from a read-only row whose values have already passed through standard Core JDBC type routing.
_Avoid_: Type handler, row mapper, result-set interceptor

**Parameter binder**:
A Mapper-parameter-specific strategy for writing a value outside Core standard routing. It is not a result-reading strategy.
_Avoid_: Type handler, row mapper, global type handler

**Bound SQL builder**:
An invocation-scoped generated-code module that constructs dynamic SQL and atomically aligns every emitted placeholder with its value and JDBC routing metadata. It owns SQL spacing and dynamic clause normalization but does not interpret Mapper syntax or provide an application query DSL.
_Avoid_: Query DSL, runtime SQL interpreter, parameter list accumulator

**Row mapper**:
A Mapper-method-specific strategy for constructing one result object from the current result row. It may combine several columns and does not bind statement parameters.
Unlike a generated result assembler, it reads the live `ResultSet` directly for an explicitly configured exceptional mapping.
_Avoid_: Type handler, parameter binder, result assembler, result-set interceptor

**Compile-time mapper index**:
The set of processor-emitted mapper identities used at startup to discover generated Mapper implementations. It is not one file, and it is not a classpath scan of `*MapperImpl` classes.
_Avoid_: classpath component scan, MapperImpl suffix scan, MyBatis mapper scan, single index file

**Mapper metadata resource**:
One processor-emitted descriptor for one generated Mapper. The compile-time mapper index is the set of these resources.
_Avoid_: Spring component index, mapper registry, MyBatis mapper XML
