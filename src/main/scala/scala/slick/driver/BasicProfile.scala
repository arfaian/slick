package scala.slick.driver

import scala.language.implicitConversions
import scala.slick.ast.{FieldSymbol, Node}
import scala.slick.compiler.QueryCompiler
import scala.slick.lifted._

trait BasicProfile extends BasicTableComponent { driver: BasicDriver =>

  // Create the different builders -- these methods should be overridden by drivers as needed
  def createQueryTemplate[P,R](q: Query[_, R]): BasicQueryTemplate[P,R] = new BasicQueryTemplate[P,R](q, this)
  def createQueryBuilder(input: QueryBuilderInput): QueryBuilder = new QueryBuilder(input)
  def createInsertBuilder(node: Node): InsertBuilder = new InsertBuilder(node)
  def createTableDDLBuilder(table: Table[_]): TableDDLBuilder = new TableDDLBuilder(table)
  def createColumnDDLBuilder(column: FieldSymbol, table: Table[_]): ColumnDDLBuilder = new ColumnDDLBuilder(column)
  def createSequenceDDLBuilder(seq: Sequence[_]): SequenceDDLBuilder = new SequenceDDLBuilder(seq)

  val compiler = QueryCompiler.relational
  val Implicit = new Implicits
  val typeMapperDelegates = new TypeMapperDelegates
  val capabilities: Set[Capability] = BasicProfile.capabilities.all

  final def createQueryBuilder(q: Query[_, _]): QueryBuilder = createQueryBuilder(new QueryBuilderInput(compiler.run(Node(q)), q))
  final def buildSelectStatement(q: Query[_, _]): QueryBuilderResult = createQueryBuilder(q).buildSelect
  final def buildUpdateStatement(q: Query[_, _]): QueryBuilderResult = createQueryBuilder(q).buildUpdate
  final def buildDeleteStatement(q: Query[_, _]): QueryBuilderResult = createQueryBuilder(q).buildDelete
  @deprecated("Use createInsertBuilder.buildInsert", "1.0")
  final def buildInsertStatement(cb: Any): InsertBuilderResult = createInsertBuilder(Node(cb)).buildInsert
  @deprecated("Use createInsertBuilder.buildInsert", "1.0")
  final def buildInsertStatement(cb: Any, q: Query[_, _]): InsertBuilderResult = createInsertBuilder(Node(cb)).buildInsert(q)
  final def buildTableDDL(table: Table[_]): DDL = createTableDDLBuilder(table).buildDDL
  final def buildSequenceDDL(seq: Sequence[_]): DDL = createSequenceDDLBuilder(seq).buildDDL

  class Implicits extends ExtensionMethodConversions {
    implicit val slickDriver: driver.type = driver
    implicit def columnToOptionColumn[T : BaseTypeMapper](c: Column[T]): Column[Option[T]] = c.?
    implicit def valueToConstColumn[T : TypeMapper](v: T) = new ConstColumn[T](v)
    implicit def tableToQuery[T <: AbstractTable[_]](t: T) = Query[T, NothingContainer#TableNothing, T](t)(Shape.tableShape)
    implicit def columnToOrdered[T](c: Column[T]): ColumnOrdered[T] = c.asc
    implicit def queryToQueryInvoker[T, U](q: Query[T, _ <: U]): QueryInvoker[T, U] = new QueryInvoker(q)
    implicit def queryToDeleteInvoker(q: Query[_ <: Table[_], _]): DeleteInvoker = new DeleteInvoker(q)
    implicit def columnBaseToInsertInvoker[T](c: ColumnBase[T]) = new CountingInsertInvoker(ShapedValue.createShapedValue(c))
    implicit def shapedValueToInsertInvoker[T, U](u: ShapedValue[T, U]) = new CountingInsertInvoker(u)

    implicit def queryToQueryExecutor[E, U](q: Query[E, U]): QueryExecutor[Seq[U]] = new QueryExecutor[Seq[U]](new QueryBuilderInput(compiler.run(Node(q)), q))

    // We can't use this direct way due to SI-3346
    def recordToQueryExecutor[M, R](q: M)(implicit shape: Shape[M, R, _]): QueryExecutor[R] = new QueryExecutor[R](new QueryBuilderInput(compiler.run(Node(q)), shape.linearizer(q)))
    implicit final def recordToUnshapedQueryExecutor[M <: Rep[_]](q: M): UnshapedQueryExecutor[M] = new UnshapedQueryExecutor[M](q)
    implicit final def anyToToQueryExecutor[T](value: T) = new ToQueryExecutor[T](value)

    // We should really constrain the 2nd type parameter of Query but that won't
    // work for queries on implicitly lifted tables. This conversion is needed
    // for mapped tables.
    implicit def tableQueryToUpdateInvoker[T](q: Query[_ <: Table[T], NothingContainer#TableNothing]): UpdateInvoker[T] = new UpdateInvoker(q.asInstanceOf[Query[Table[T], T]])

    // This conversion only works for fully packed types
    implicit def productQueryToUpdateInvoker[T](q: Query[_ <: ColumnBase[T], T]): UpdateInvoker[T] = new UpdateInvoker(q)

    // Work-around for SI-3346
    @inline implicit final def anyToToShapedValue[T](value: T) = new ToShapedValue[T](value)
  }

  class SimpleQL extends Implicits with scala.slick.lifted.Aliases {
    type Table[T] = driver.Table[T]
    type Database = scala.slick.session.Database
    val Database = scala.slick.session.Database
    type Session = scala.slick.session.Session
    type SlickException = scala.slick.SlickException
  }

  /** A collection of values for using the query language with a single import
    * statement. This provides the driver's implicits, the Database and
    * Session objects for DB connections, and commonly used query language
    * types and objects. */
  val simple = new SimpleQL
}

object BasicProfile {
  object capabilities {
    /** Supports the Blob data type */
    val blob = Capability("basic.blob")
    /** Supports default values in column definitions */
    val columnDefaults = Capability("basic.columnDefaults")
    /** Supports .drop on queries */
    val pagingDrop = Capability("basic.pagingDrop")
    /** Supports properly compositional paging in sub-queries */
    val pagingNested = Capability("basic.pagingNested")
    /** Supports mutable result sets */
    val mutable = Capability("basic.mutable")
    /** Supports sequences (real or emulated) */
    val sequence = Capability("basic.sequence")
    /** Can get current sequence value */
    val currval = Capability("basic.currval")
    /** Supports zip, zipWith and zipWithIndex */
    val zip = Capability("basic.zip")

    /** Supports all BasicProfile features which do not have separate capability values */
    val basic = Capability("basic")
    /** All basic capabilities */
    val all = Set(basic, blob, columnDefaults, pagingDrop, pagingNested, mutable, sequence, currval, zip)
  }
}

trait BasicDriver extends BasicProfile
  with BasicStatementBuilderComponent
  with BasicTypeMapperDelegatesComponent
  with BasicSQLUtilsComponent
  with BasicExecutorComponent
  with BasicInvokerComponent{
  val profile: BasicProfile = this
}

object BasicDriver extends BasicDriver
