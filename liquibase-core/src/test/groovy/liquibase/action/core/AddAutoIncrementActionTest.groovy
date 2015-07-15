package liquibase.action.core

import liquibase.JUnitScope
import liquibase.action.ActionStatus
import liquibase.action.TestObjectFactory
import liquibase.actionlogic.ActionExecutor
import liquibase.actionlogic.ActionResult
import liquibase.database.ConnectionSupplierFactory
import liquibase.snapshot.MockSnapshotFactory
import liquibase.snapshot.SnapshotFactory
import liquibase.snapshot.TestSnapshotFactory
import liquibase.snapshot.transformer.LimitTransformer
import liquibase.structure.ObjectName
import liquibase.structure.core.Column
import liquibase.util.CollectionUtil
import org.junit.Assume
import spock.lang.Unroll

class AddAutoIncrementActionTest extends AbstractActionTest {

    @Unroll("#featureName: #column on #conn")
    def "Can apply standard settings to complex names"() {
        when:
        def action = new AddAutoIncrementAction()
        action.columnName = column.name
        action.columnDataType = column.type

        then:
        def plan = new ActionExecutor().createPlan(action, scope)

        testMDPermutation(snapshot, conn, scope)
                .addParameters([
                columnName_asTable: action.columnName.toString()
        ])
                .addOperations(plan: plan)
                .run({
            plan.execute(scope)

            assert scope.getSingleton(ActionExecutor).checkStatus(action, scope).applied
        })

        where:
        [conn, scope, snapshot, column] << JUnitScope.instance.getSingleton(ConnectionSupplierFactory).connectionSuppliers.collectMany {
            def scope = JUnitScope.getInstance(it).child(JUnitScope.Attr.objectNameStrategy, JUnitScope.TestObjectNameStrategy.COMPLEX_NAMES)
            def snapshot = JUnitScope.instance.getSingleton(TestSnapshotFactory).createSnapshot(new LimitTransformer(2), scope)
            return CollectionUtil.permutations([
                    [it],
                    [scope],
                    [snapshot],
                    snapshot.get(Column)
            ])
        }

    }

    @Unroll("#featureName: #action for #column on #conn")
    def "Valid parameter permutations work"() {
        when:
        action.columnName = column.name
        action.columnDataType = column.type

        then:
        def errors = new ActionExecutor().validate(action, scope)
        Assume.assumeFalse(errors.toString(), errors.hasErrors())

        def plan = new ActionExecutor().createPlan(action, scope)

        testMDPermutation(snapshot, conn, scope)
                .addParameters([
                columnName_asTable : action.columnName.toString(),
                startWith_asTable  : action.startWith,
                incrementBy_asTable: action.incrementBy
        ])
                .addOperations(plan: plan)
                .run({

            if (action.incrementBy != null) { //need to check because checkStatus does not get incrementBy metadata
                assert plan.toString().contains(action.incrementBy.toString()) : "IncrementBy value not used"
            }
            if (action.startWith != null) { //need to check because checkStatus does not get startWith metadata
                assert plan.toString().contains(action.startWith.toString()) : "StartWith value not used"
            }

            plan.execute(scope)

            assert scope.getSingleton(ActionExecutor).checkStatus(action, scope).applied
        })

        where:
        [conn, scope, snapshot, column, action] << JUnitScope.instance.getSingleton(ConnectionSupplierFactory).connectionSuppliers.collectMany {
            def scope = JUnitScope.getInstance(it)
            def snapshot = JUnitScope.instance.getSingleton(TestSnapshotFactory).createSnapshot(new LimitTransformer(2), scope)
            return CollectionUtil.permutations([
                    [it],
                    [scope],
                    [snapshot],
                    snapshot.get(Column),
                    JUnitScope.instance.getSingleton(TestObjectFactory).createAllPermutations(AddAutoIncrementAction, [
                            columnName    : null,
                            columnDataType: null,
                            startWith     : [null, 1, 2, 10],
                            incrementBy   : [null, 1, 5, 20]
                    ]),
            ])
        }

    }
}
