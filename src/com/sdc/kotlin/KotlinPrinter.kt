package KotlinPrinter

import pretty.*
import com.sdc.ast.expressions.Expression
import com.sdc.ast.expressions.Constant
import com.sdc.ast.expressions.BinaryExpression
import com.sdc.ast.expressions.UnaryExpression
import com.sdc.ast.expressions.identifiers.Field
import com.sdc.ast.expressions.identifiers.Variable
import com.sdc.ast.controlflow.Statement
import com.sdc.ast.controlflow.Invocation
import com.sdc.ast.controlflow.Assignment
import com.sdc.ast.controlflow.Return
import com.sdc.ast.controlflow.Throw
import com.sdc.ast.expressions.New
import com.sdc.ast.controlflow.InstanceInvocation
import com.sdc.ast.expressions.NewArray
import com.sdc.kotlin.KotlinClass
import com.sdc.kotlin.KotlinMethod

fun printExpression(expression: Expression?, nestSize: Int): PrimeDoc =
        when (expression) {
            is Constant ->
                if (!expression.isStringValue())
                    text(expression.getValue().toString())
                else
                    text("\"" + expression.getValue().toString() + "\"")
            is BinaryExpression -> {
                val opPriority = expression.getPriority()

                val l = expression.getLeft()
                val left = when (l) {
                    is BinaryExpression ->
                        if (opPriority - l.getPriority() < 2)
                            printExpression(l, nestSize)
                        else
                            printExpressionWithBrackets(l, nestSize)
                    else -> printExpression(l, nestSize)
                }

                val r = expression.getRight()
                val right = when (r) {
                    is BinaryExpression ->
                        if (opPriority - r.getPriority() > 0 || opPriority == 3)
                            printExpressionWithBrackets(r, nestSize)
                        else
                            printExpression(r, nestSize)
                    else -> printExpression(r, nestSize)
                }

                group(left / (text(expression.getOperation()) + right))
            }

            is UnaryExpression -> {
                val operand = expression.getOperand()
                val expr = when (operand) {
                    is BinaryExpression ->
                        if (operand.getPriority() < 2)
                            printExpressionWithBrackets(operand, nestSize)
                        else
                            printExpression(operand, nestSize)
                    else -> printExpression(operand, nestSize)
                }
                text(expression.getOperation()) + expr
            }

            is Field -> text(expression.getName())
            is Variable -> {
                if (expression.getArrayIndex() == null)
                    text(expression.getName())
                else {
                    group(
                            printExpression(expression.getArrayVariable(), nestSize)
                            + text("[") + printExpression(expression.getArrayIndex(), nestSize) + text("]")
                    )
                }
            }

            is com.sdc.ast.expressions.Invocation -> {
                var funName = group(text(expression.getFunction() + "("))
                if (expression is InstanceInvocation) {
                    funName = group(text(expression.getVariable()!!.getName() + ".") + funName)
                }
                val args = expression.getArguments()
                if (args!!.isEmpty())
                    funName + text(")")
                else {
                    var argsDocs = args.take(args.size - 1)
                            .map { arg -> printExpression(arg, nestSize) + text(", ") }
                    var arguments = nest(2 * nestSize, fill(argsDocs + printExpression(args.last, nestSize)))

                    group(funName + arguments + text(")"))
                }
            }

            is New -> group(
                    text("new") + nest(nestSize, line()
                    + printExpression(expression.getConstructor(), nestSize))
            )
            is NewArray -> {
                var newArray = group(text("new") + nest(nestSize, line() + text(expression.getType())))
                for (dimension in expression.getDimensions()!!.toList()) {
                    newArray = group(newArray + text("[") + printExpression(dimension, nestSize) + text("]"))
                }
                newArray
            }
            else -> throw IllegalArgumentException("Unknown Expression implementer!")
        }

fun printStatement(statement: Statement, nestSize: Int): PrimeDoc =
        when (statement) {
            is Invocation -> {
                var funName = group(text(statement.getFunction() + "("))
                if (statement is InstanceInvocation) {
                    funName = group(text(statement.getVariable()!!.getName() + ".") + funName)
                }
                val args = statement.getArguments()
                if (args!!.isEmpty())
                    funName + text(")")
                else {
                    var argsDocs = args.take(args.size - 1)
                            .map { arg -> printExpression(arg, nestSize) + text(", ") }
                    var arguments = nest(2 * nestSize, fill(argsDocs + printExpression(args.last as Expression, nestSize)))

                    group(funName + arguments + text(")"))
                }
            }
            is Assignment -> group(
                    (printExpression(statement.getLeft(), nestSize) + text(" ="))
                    + nest(nestSize, line() + printExpression(statement.getRight(), nestSize))
            )
            is Return -> if (statement.getReturnValue() != null)
                group(
                        text("return") + nest(nestSize, line()
                        + printExpression(statement.getReturnValue(), nestSize))
                )
            else
                text("return")
            is Throw -> group(
                    text("throw") + nest(nestSize, line()
                    + printExpression(statement.getThrowObject(), nestSize))
            )

            else -> throw IllegalArgumentException("Unknown Statement implementer!")
        }

fun printStatements(statements: List<Statement>?, nestSize: Int): PrimeDoc {
    if ((statements == null) || (statements.size == 0))
        return nil()
    else {
        var body = printStatement(statements.get(0), nestSize)
        for (i in 1..statements.size - 1) {
            body = group(body / printStatement(statements.get(i), nestSize))
        }
        return body
    }
}

fun printExpressionWithBrackets(expression: Expression, nestSize: Int): PrimeDoc =
        text("(") + printExpression(expression, nestSize) + text(")")

fun printKotlinClass(kotlinClass: KotlinClass): PrimeDoc {
    val packageCode = text("package " + kotlinClass.getPackage())

    var imports : PrimeDoc = nil()
    for (importName in kotlinClass.getImports()!!.toArray())
        imports = imports / text("import " + importName)

    var declaration = group(text(kotlinClass.getModifier() + kotlinClass.getType() + kotlinClass.getName()))

    val genericsDeclaration = kotlinClass.getGenericDeclaration()
    if (!genericsDeclaration!!.isEmpty()) {
        declaration = group(declaration + text("<"))
        var oneType = true
        for (genericType in genericsDeclaration) {
            if (!oneType)
                declaration = group(declaration + text(", "))
            declaration = group(declaration + text(genericType))
            oneType = false
        }
        declaration = group(declaration + text(">"))
    }

    val superClass = kotlinClass.getSuperClass()
    if (!superClass!!.isEmpty())
        declaration = group(declaration + nest(kotlinClass.getNestSize(), line() + text(": " + superClass)))

    val implementedInterfaces = kotlinClass.getImplementedInterfaces()!!.toArray()
    for (interface in implementedInterfaces) {
        declaration = group(
                (declaration + text(","))
                + nest(2 * kotlinClass.getNestSize(), line() + text(interface as String))
        )
    }

    var kotlinClassCode = packageCode + imports / group(declaration + text(" {"))

    for (classField in kotlinClass.getFields()!!.toArray())
        kotlinClassCode = group(
                kotlinClassCode
                + nest(kotlinClass.getNestSize(), line())
        )

    for (classMethod in kotlinClass.getMethods()!!.toList())
        kotlinClassCode = group(
                kotlinClassCode
                + nest(kotlinClass.getNestSize(), line() + printKotlinMethod(classMethod))
        )

    return kotlinClassCode / text("}")
}

fun printKotlinMethod(kotlinMethod: KotlinMethod): PrimeDoc {
    var declaration : PrimeDoc = text(kotlinMethod.getModifier() + "fun ")

    val genericsDeclaration = kotlinMethod.getGenericDeclaration()
    if (!genericsDeclaration!!.isEmpty()) {
        declaration = declaration + text("<")
        var oneType = true
        for (genericType in genericsDeclaration) {
            if (!oneType)
                declaration = declaration + text(", ")
            declaration = declaration + text(genericType)
            oneType = false
        }
        declaration = declaration + text("> ")
    }
    declaration = declaration + text(kotlinMethod.getName() + "(")

    var arguments: PrimeDoc = nil()
    if (kotlinMethod.getLastLocalVariableIndex() != 0) {
        var variables = kotlinMethod.getParameters()!!.toList()
        var index = 0
        for (variable in variables) {
            arguments = nest(
                    2 * kotlinMethod.getNestSize()
                    , arguments + text(variable)
            )
            if (index + 1 < variables.size)
                arguments = group(arguments + text(",") + line())

            index++
        }
    }

    val body = text("}")

    return group(declaration + arguments + text(")") + text(": " + kotlinMethod.getReturnType() + " ") + text("{")) + body
}