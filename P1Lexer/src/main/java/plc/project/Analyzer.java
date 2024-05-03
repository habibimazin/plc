package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void>
{

    public Scope scope;
    private Ast.Function function;

    public Analyzer(Scope parent)
    {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast)
    {
        //flag to check if main/0 function exists
        boolean mainExists = false;
        List<Ast.Global> globals = ast.getGlobals();

        for(int i = 0; i<globals.size(); i++){
            visit(globals.get(i));
        }

        //iterates through functions in the ast
        for (Ast.Function function : ast.getFunctions())
        {
            //checks if the function is named "main" and has no parameters
            if ("main".equals(function.getName()) && function.getParameters().isEmpty())
            {
                mainExists = true;

                //checks if the return type of main function is not int
                if (!"Integer".equals(function.getReturnTypeName().orElse("")))
                    //thrwo error if no returned int
                    throw new RuntimeException("The main function must return am int");
            }
            //visit the current function
            visit(function);
        }

        //throw error RuntimeException if main/0 function does not exist
        if (!mainExists)
            throw new RuntimeException("Missing main/0 function");

        //required null
        return null;
    }


    @Override
    public Void visit(Ast.Global ast)
    {
        try
        {
            String valueTypes="";
            //get the type of variable from env
            Environment.Type variableType = Environment.getType(ast.getTypeName());

            //default is nil
            Environment.PlcObject initialValue = Environment.NIL;

            //if  a value for global
            if (ast.getValue().isPresent())
            {
                Ast.Expression valueExpression = ast.getValue().get();
                visit(valueExpression);
                //get new type and is compatible
                try {
                    Environment.Type valueType = valueExpression.getType();
                    requireAssignable(variableType, valueType);
                }
                catch (RuntimeException e){
                    Ast.Expression.PlcList list = (Ast.Expression.PlcList) valueExpression;
                    List vals = list.getValues();
                    for(int i = 0; i< vals.size(); i++){
                        Ast.Expression.Literal lit = (Ast.Expression.Literal) vals.get(i);
                        Environment.Type vType = lit.getType();
                        requireAssignable(variableType, vType);
                    }


                }
            }

            //make sure global variable is in the scope
            Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), variableType, ast.getMutable(), initialValue);
            ast.setVariable(variable);
        }
        //throw
        catch (RuntimeException e)
        {
            throw new RuntimeException("Value assigned to global variable '" + ast.getName() + "' is not assignable to " + e.getMessage());
        }
        return null;
    }



    @Override
    public Void visit(Ast.Function ast)
    {
        this.function = ast;

        Environment.Type returnType = ast.getReturnTypeName()
                .map(Environment::getType)
                .orElse(Environment.Type.NIL);

        List<Environment.Type> parameterTypes = ast.getParameterTypeNames().stream()
                .map(Environment::getType)
                .collect(Collectors.toList());
        //parameterTypes.get(0);


        Environment.Function function = scope.defineFunction(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
        ast.setFunction(function);



        Scope functionScope = new Scope(scope);
        scope = functionScope;

        /*
        for(int i = 0; i<ast.getParameters().size(); i++){
            String varname = ast.getParameters().get(i);
            String varjvm = parameterTypes.get(i).getJvmName();
            String vartypes = ast.getParameterTypeNames().get(i);
            Environment.Type t = parameterTypes.get(i);
            Environment.PlcObject ob = new Environment.PlcObject(scope, Optional.empty());
            scope.defineVariable(varname,varjvm, t,true, ob);
        }

         */





        for (Ast.Statement statement : ast.getStatements())
            visit(statement);

        scope = scope.getParent();
        this.function = null;

        return null;
    }




    @Override
    public Void visit(Ast.Statement.Expression ast)
    {
        visit(ast.getExpression());
        return null;
    }



    @Override
    public Void visit(Ast.Statement.Declaration ast)
    {
        Environment.Type type = null;

        if (ast.getTypeName().isPresent())
        {
            String typeName = ast.getTypeName().get();
            try
            {
                type = Environment.getType(typeName);
            }
            catch (IllegalArgumentException e)
            {
                throw new RuntimeException("Unknown type: " + typeName);
            }
        }

        else if (ast.getValue().isPresent())
        {

            visit(ast.getValue().get());
            type = ast.getValue().get().getType();
        }

        if (type == null)
            throw new RuntimeException("Declaration of " + ast.getName() + " lacks both type and initial value");

        if (ast.getValue().isPresent())
            requireAssignable(type, ast.getValue().get().getType());

        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type, true, Environment.NIL);
        ast.setVariable(variable);

        return null;
    }




    @Override
    public Void visit(Ast.Statement.Assignment ast)
    {
        //if its an access expression
        if (!(ast.getReceiver() instanceof Ast.Expression.Access))
            //throw if not
            throw new RuntimeException("Invalid assignment statement, receiver must be an access expression");

        //check if value is assignable to receiver
        Ast.Expression.Access receiver = (Ast.Expression.Access) ast.getReceiver();
        visit(receiver);

        Ast.Expression valueExpression = ast.getValue();
        visit(valueExpression); //evaluate

        //compatible
        Environment.Variable variable = receiver.getVariable();
        requireAssignable(variable.getType(), valueExpression.getType());

        return null;
    }



    @Override
    public Void visit(Ast.Statement.While ast)
    {
        Ast.Expression condition = ast.getCondition();
        //System.out.println("Evaluating While condition: " + condition.getClass().getSimpleName());

        if (!((condition instanceof Ast.Expression.Literal) || condition instanceof Ast.Expression.Access|| condition instanceof Ast.Expression.Binary))
            throw new RuntimeException("While loop condition must be a binary expression");

        visit(condition);

        if (!condition.getType().equals(Environment.Type.BOOLEAN))
            throw new RuntimeException("While loop condition must evaluate to a boolean");

        for (Ast.Statement statement : ast.getStatements())
            visit(statement);

        return null;
    }


    @Override
    public Void visit(Ast.Statement.Return ast)
    {
        if (this.function == null)
            throw new RuntimeException("Return statement not within any function");

        Environment.Type expectedReturnType = this.function.getFunction().getReturnType();

        if (ast.getValue() != null)
        {
            Ast.Expression valueExpression = ast.getValue();
            visit(valueExpression);

            Environment.Type valueType = valueExpression.getType();

            if (!expectedReturnType.equals(valueType))
            {
                throw new RuntimeException("Function expected to return type " + expectedReturnType.getName() +
                        ", but returned type " + valueType.getName());
            }
        }
        else
        {
            if (!expectedReturnType.equals(Environment.Type.NIL))
                throw new RuntimeException("Function '" + this.function.getName() + "' must return a value of type " + expectedReturnType.getName());
        }
        return null;
    }



    @Override
    public Void visit(Ast.Expression.Literal ast)
    {
        Object literal = ast.getLiteral();

        if (literal instanceof Boolean)
            ast.setType(Environment.Type.BOOLEAN);

        else if (literal instanceof Character)
            ast.setType(Environment.Type.CHARACTER);

        else if (literal instanceof Integer)
            ast.setType(Environment.Type.INTEGER);

        else if (literal instanceof String)
        {
            String stringValue = (String) literal;
            ast.setType(Environment.Type.STRING);
        }

        else if (literal instanceof BigInteger)
        {
            BigInteger value = (BigInteger) literal;
            if (value.bitLength() <= 31)
                ast.setType(Environment.Type.INTEGER);
            else
                throw new RuntimeException("Integer value out of range for 32-bit signed integer");
        }

        else if (literal instanceof BigDecimal)
        {
            BigDecimal decimal = (BigDecimal) literal;
            try
            {
                decimal.doubleValue();
                ast.setType(Environment.Type.DECIMAL);
            }
            catch (ArithmeticException e)
            {
                throw new RuntimeException("Decimal value out of range for 64-bit double");
            }
        }
        else
            throw new RuntimeException("Unrecognized literal type");

        return null;
    }


    @Override
    public Void visit(Ast.Expression.Group ast)
    {
        Ast.Expression expression = ast.getExpression();
        //check expression
        if (!(expression instanceof Ast.Expression.Binary))
            //throw if not binary
            throw new RuntimeException("Group expression must have a binary expression");
        visit(expression);

        //make the group expressions type to be the type of the contained expression
        ast.setType(expression.getType());

        return null;
    }


    @Override
    public Void visit(Ast.Expression.Binary ast)
    {
        //System.out.println("Visiting binary expression with operator: " + ast.getOperator());

        visit(ast.getLeft());
        visit(ast.getRight());

        Environment.Type leftType = ast.getLeft().getType();
        Environment.Type rightType = ast.getRight().getType();
        String operator = ast.getOperator();
        switch (operator)
        {
            case "&&":
            case "||":
                if (leftType != Environment.Type.BOOLEAN || rightType != Environment.Type.BOOLEAN)
                    throw new RuntimeException("Logical operators require boolean operands, found " + leftType + " and " + rightType);
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "+":
                if (leftType == Environment.Type.STRING || rightType == Environment.Type.STRING)
                    ast.setType(Environment.Type.STRING);

                else if (leftType == Environment.Type.INTEGER && rightType == Environment.Type.INTEGER)
                    ast.setType(Environment.Type.INTEGER);

                else if (leftType == Environment.Type.DECIMAL && rightType == Environment.Type.DECIMAL)
                    ast.setType(Environment.Type.DECIMAL);
                else
                    throw new RuntimeException("Invalid operands for addition, found " + leftType + " and " + rightType);
                break;

            case "<":
            case ">":
            case "==":
            case "!=":
                if (!leftType.equals(rightType))
                    throw new RuntimeException("Comparison operators require operands of the same type, found " + leftType + " and " + rightType);
                ast.setType(Environment.Type.BOOLEAN);
                break;

            case "-":
                if (leftType == Environment.Type.INTEGER &&rightType==Environment.Type.INTEGER){
                    ast.setType(Environment.Type.INTEGER);
                }
                else if (leftType == Environment.Type.DECIMAL &&rightType==Environment.Type.DECIMAL){
                    ast.setType(Environment.Type.DECIMAL);
                }
                else{
                    throw new RuntimeException("Both operands for - must be integer or decimal and match");
                }
                break;
            case "*":
                if (leftType == Environment.Type.INTEGER &&rightType==Environment.Type.INTEGER){
                    ast.setType(Environment.Type.INTEGER);
                }
                else if (leftType == Environment.Type.DECIMAL &&rightType==Environment.Type.DECIMAL){
                    ast.setType(Environment.Type.DECIMAL);
                }
                else{
                    throw new RuntimeException("Both operands for * must be integer or decimal and match");
                }
                break;
            case "/":
                if (leftType == Environment.Type.INTEGER &&rightType==Environment.Type.INTEGER){
                    ast.setType(Environment.Type.INTEGER);
                }
                else if (leftType == Environment.Type.DECIMAL &&rightType==Environment.Type.DECIMAL){
                    ast.setType(Environment.Type.DECIMAL);
                }
                else{
                    throw new RuntimeException("Both operands for / must be integer or decimal and match");
                }
                break;
            case "^":
                if (leftType == Environment.Type.INTEGER &&rightType==Environment.Type.INTEGER){
                    ast.setType(Environment.Type.INTEGER);
                }
                else{
                    throw new RuntimeException("^ needs to be of type integer");
                }
                break;
            default:
                throw new RuntimeException("Unsupported binary operator: " + ast.getOperator());
        }
        return null;
    }







    @Override
    public Void visit(Ast.Expression.Access ast)
    {
        // if an offset, check if the access is to a variable or an element in a list
        if (ast.getOffset().isPresent())
        {
            Ast.Expression offset = ast.getOffset().get();
            visit(offset);
            if (!offset.getType().equals(Environment.Type.INTEGER))
                //throw it not int
                throw new RuntimeException("List index must be an int");

            //get var accessed type if in list
            Environment.Variable variable = scope.lookupVariable(ast.getName());
            if (variable == null)
                //throw if var is not defined
                throw new RuntimeException("Variable " + ast.getName() + " is not defined.");
            ast.setVariable(variable);
        }
        else
        {
            Environment.Variable variable = scope.lookupVariable(ast.getName());
            if (variable == null)
                //throw if not defined
                throw new RuntimeException("Variable " + ast.getName() + " is not defined.");
            ast.setVariable(variable);
        }
        return null;
    }


    @Override
    public Void visit(Ast.Expression.PlcList ast)

    {
        Environment.Type listType = null;
        String listType2 = "";


        try {
            listType = ast.getType();
        }
        catch(RuntimeException e){
            Ast.Expression.Literal lit = (Ast.Expression.Literal) ast.getValues().get(0);
            listType2 = lit.getLiteral().getClass().toString();

            for (Ast.Expression expression : ast.getValues())
            {
                visit(expression);
                Ast.Expression.Literal lit2 = (Ast.Expression.Literal) expression;
                //get type of expression
                String expressionType = lit2.getLiteral().getClass().toString();

                //compatible
                if (!expressionType.equals(listType2))
                    //throw if type mismatch
                    throw new RuntimeException("Type mismatch: Cannot add " + expressionType + " to a list of " + listType.getName());
            }
            return null;
        }


        //System.out.println(listType2);

        for (Ast.Expression expression : ast.getValues())
        {
            visit(expression);

            //get type of expression
            Environment.Type expressionType = expression.getType();

            //compatible
            if (!expressionType.equals(listType) && !listType.equals(Environment.Type.ANY))
                //throw if type mismatch
                throw new RuntimeException("Type mismatch: Cannot add " + expressionType.getName() + " to a list of " + listType.getName());
        }
        return null;
    }


    // make sure type compatibility is within our grammar
    public static void requireAssignable(Environment.Type target, Environment.Type type)
    {
        boolean isAssignable = false;

        //types are  same
        if (type.equals(target))
            isAssignable = true;

            //anything can be assigned to Any
        else if (target.equals(Environment.Type.ANY))
            isAssignable = true;

            //integer, decimal, character, and string are Comparable
        else if (target.equals(Environment.Type.COMPARABLE) &&
                (type.equals(Environment.Type.INTEGER) ||
                        type.equals(Environment.Type.DECIMAL) ||
                        type.equals(Environment.Type.CHARACTER) ||
                        type.equals(Environment.Type.STRING)))

            isAssignable = true;

        //if none throw the runtime exception
        if (!isAssignable)
            throw new RuntimeException("Type " + type.getName() + " is not assignable to " + target.getName());
    }


    @Override
    public Void visit(Ast.Statement.If ast)
    {
        Scope origScope = scope;

        try
        {
            Ast a = ast.getCondition();
            Ast.Expression.Literal x = (Ast.Expression.Literal) ast.getCondition();
            Class y = x.getLiteral().getClass();
            if(y.equals(Boolean.class))
                visit(a);
            else
            {
                throw new java.lang.RuntimeException("Not Boolean!");
            }

        }
        catch (ClassCastException e)
        {
            try {
                Ast.Expression.Access bin = (Ast.Expression.Access) ast.getCondition();
                Environment.Variable bin2 = scope.lookupVariable(bin.getName());
                Environment.Type t = bin2.getType();
                if(!t.equals(Environment.Type.BOOLEAN)){
                    throw new java.lang.RuntimeException("Not Boolean!");
                }
                visit(ast.getCondition());
            } catch (ClassCastException er){
                throw new java.lang.RuntimeException("Not Boolean!");
            }
        }

        List<Ast.Statement> thenstatements = ast.getThenStatements();
        if(thenstatements.size() == 0)
            throw new java.lang.RuntimeException();
        else
        {
            try
            {
                scope = new Scope(scope);
                for (int i = 0; i < thenstatements.size(); i++)
                {
                    Ast thens = thenstatements.get(i);
                    visit(thens);
                }
            }
            finally
            {
                scope = origScope;
            }
        }

        List<Ast.Statement> elsestatements = ast.getElseStatements();

        try
        {
            scope = new Scope(scope);
            for (int i = 0; i < elsestatements.size(); i++)
            {
                Ast elses = elsestatements.get(i);
                visit(elses);
            }
        }

        finally
        {
            scope = origScope;
        }

        return null;

    }
    public static boolean matchesSimpleClassName(String fullClassName, String simpleClassName)
    {
        String regex = "class java\\.lang\\.(.*)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(fullClassName);

        if (matcher.find())
        {
            return simpleClassName.equals(matcher.group(1));

        } else if (fullClassName.equals(simpleClassName)) {
            return true;
        }
        return false;
    }


    @Override
    public Void visit(Ast.Statement.Switch ast)
    {
        Scope origScope = scope;

        visit(ast.getCondition());
        List<Ast.Statement.Case> cases = ast.getCases();
        String t = "";
        //ast.expressioast.getCondition();
        try {
            Ast.Expression.Access val = (Ast.Expression.Access) ast.getCondition();
            //System.out.println("THIS IS THE TYPE");
            //System.out.println(val.getType());
            Environment.Variable var = scope.lookupVariable(val.getName());
            t = var.getType().getName();
            //System.out.println(t);
        }
        catch (ClassCastException e){
            try{
                Ast.Expression.Literal lit = (Ast.Expression.Literal) ast.getCondition();
                t = lit.getLiteral().getClass().toString();
            } catch(ClassCastException er) {
                t = ast.getCondition().getType().getName();
            }
        }

        for(int i = 0; i<cases.size(); i++) {
            if(i != cases.size()-1) {
                Ast.Expression.Literal y = (Ast.Expression.Literal) cases.get(i).getValue().get();
                String classtype = y.getLiteral().getClass().toString();
                if (!matchesSimpleClassName(classtype, t)) {
                    throw new RuntimeException("Condition Type did not match with Case value type at index: " + i);
                }
            }
            else{
                try{
                    Ast.Expression.Literal y = (Ast.Expression.Literal) cases.get(i).getValue().get();
                    throw new RuntimeException("Value should not exist at Default case statement");
                }
                catch (NoSuchElementException e){

                }

            }
            try {
                scope = new Scope(scope);
                visit(cases.get(i));
            }
            finally {
                scope = origScope;
            }


            //System.out.println("classtype");
        }

        return null;
    }


    @Override
    public Void visit(Ast.Statement.Case ast) {
        List<Ast.Statement> statements = ast.getStatements();
        try {
            visit(ast.getValue().get());
        }
        catch (NoSuchElementException e){
        }
        for (int i = 0; i< statements.size(); i++){
            Ast.Statement stat = statements.get(i);
            visit(stat);
        }
        return null;
    }


    @Override
    public Void visit(Ast.Expression.Function ast) {

        Environment.Function x = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        List<Environment.Type> types = x.getParameterTypes();
        List<Ast.Expression> args = ast.getArguments();
        for(int i = 0; i< args.size(); i++){
            visit(args.get(i));
        }
        //Environment.Type acc = ast.getArguments().get(0).getType();
        ast.setFunction(x);
        //System.out.println(ast.toString());


        return null;
    }

}