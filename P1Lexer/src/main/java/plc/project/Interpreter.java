package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        List<Ast.Global> globals = ast.getGlobals();
        List<Ast.Function> functions = ast.getFunctions();

        for(int i = 0; i<globals.size(); i++){
            visit(globals.get(i));
        }
        //Ast.Function fun = ast.getFunctions();
        for(int i = 0; i<functions.size(); i++) {
            Ast.Function fun = functions.get(i);
            Object name = fun.getName();
            if(name.equals("main")){
                for(int a = 0; a<fun.getStatements().size(); a++) {
                    Ast.Statement statement = fun.getStatements().get(i);
                    try {
                        visit(statement);
                    }
                    catch (Return returnValue){
                        return returnValue.value;
                    }
                }
            }
            else {
                visit(fun);
            }
        }



        return Environment.NIL;

    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        if(ast.getValue().isPresent())
        {
            scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
        }
        else
        {
            scope.defineVariable(ast.getName(), false, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {

        Scope currentScope = getScope();

        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            scope = new Scope(getScope());
            try {
                for (int i = 0; i < ast.getParameters().size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), true, args.get(i));
                }

                List<Ast.Statement> statements = ast.getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    Ast.Statement statement = statements.get(i);
                    visit(statement);
                }
            }
            catch (Return returnValue){
                scope = currentScope;
                return returnValue.value;
            }
            scope = currentScope;
            return Environment.NIL;
        });

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        ast.getExpression();
        //System.out.println("Hello, World!");
        Ast.Expression.Function exp = null;
        if(ast.getExpression().getClass().equals(Ast.Expression.Binary.class)){
            visit(ast.getExpression());
        }
        else if(ast.getExpression().getClass().equals(Ast.Expression.Function.class)){
            exp = (Ast.Expression.Function) ast.getExpression();
            if(exp.getName().equals("print")){
                for(int i = 0; i<exp.getArguments().size(); i++){
                    Ast.Expression.Literal lit = (Ast.Expression.Literal) exp.getArguments().get(i);
                    System.out.println(lit.getLiteral());
                }
            }
            else{
                visit(exp);
            }
        }


        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        if(ast.getValue().isPresent())
        {
            scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
        }
        else
        {
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Environment.PlcObject visit(Ast.Statement.Assignment ast)
    {
        Ast.Expression.Access ax = null;
        Environment.PlcObject ob = null;

        //check if  receiver is of type Access before the casting
        if (ast.getReceiver() instanceof Ast.Expression.Access) {
            ax = (Ast.Expression.Access) ast.getReceiver();
        } else {
            throw new RuntimeException("Assignment receiver is not an Access expression.");
        }

        //visit value to get its eval Environment.PlcObject
        ob = visit(ast.getValue());

        if (ax.getOffset().equals(Optional.empty())) {
            Environment.Variable var = scope.lookupVariable(ax.getName());
            var.setValue(ob);
        } else {
            //make sure offset is a Literal before casting
            if (ax.getOffset().get() instanceof Ast.Expression.Literal) {
                Ast.Expression.Literal offset = (Ast.Expression.Literal) ax.getOffset().get();
                try {
                    int lit = Integer.parseInt(offset.getLiteral().toString());
                    Environment.Variable var = scope.lookupVariable(ax.getName());
                    List<Object> list;
                    //make sure var value is a list before cast
                    if (var.getValue().getValue() instanceof List) {
                        list = (List<Object>) var.getValue().getValue();
                        Object val = ob.getValue();
                        list.set(lit, val);
                    } else {
                        throw new RuntimeException("Variable value is not a list.");
                    }
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Offset for list access is not an integer.");
                }
            } else {
                throw new RuntimeException("Offset in Access expression is not a Literal.");
            }
        }
        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        Class<Ast.Expression.Literal> lit = Ast.Expression.Literal.class;
        Object ob = ast.getCondition();
        Environment.PlcObject boolcondition = new Environment.PlcObject(scope, ob);
        System.out.println(boolcondition.getValue());
        requireType(lit, boolcondition);
        Ast.Expression.Literal condition = (Ast.Expression.Literal) boolcondition.getValue();
        Scope oldscope = scope;

        if(condition.getLiteral().equals(true)){
            scope = new Scope(scope);
            List<Ast.Statement> then = ast.getThenStatements();
            for(int i = 0; i<then.size(); i++){
                visit(then.get(i));
            }
            scope = oldscope;
        }
        else if(condition.getLiteral().equals(false)){
            scope = new Scope(scope);
            List<Ast.Statement> then = ast.getElseStatements();
            for(int i = 0; i<then.size(); i++){
                visit(then.get(i));
            }
            scope = oldscope;
        }


        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        List <Ast.Statement.Case> cases = ast.getCases();

        Scope oldscope = scope;
        scope = new Scope(scope);
        Environment.PlcObject ob = visit(ast.getCondition());
        //Ast.Expression.Literal lit = new Ast.Expression.Literal(ob);

        scope.defineVariable("condition", true, ob);


        for (int i = 0; i<cases.size(); i++){
            Ast.Expression.Literal val = (Ast.Expression.Literal) cases.get(i).getValue().get();
            Environment.Variable var = scope.lookupVariable("condition");
            if (var.getValue().getValue().equals(val.getLiteral())) {
                visit(cases.get(i));
                break;
            }
        }

        scope = oldscope;
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        if(ast.getValue().equals(Optional.empty())){
            List<Ast.Statement> statements = ast.getStatements();
            for (int i = 0; i<statements.size(); i++){
                visit(statements.get(i));
            }
        }
        else {

            List<Ast.Statement> statements = ast.getStatements();
            for (int i = 0; i < statements.size(); i++) {
                    visit(statements.get(i));
            }
        }
        //System.out.println(ast.getStatements());
        return Environment.NIL;

    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while(requireType(Boolean.class, visit(ast.getCondition())))
        {
            try
            {
                scope = new Scope(scope);
                ast.getStatements().forEach(this::visit);
            }

            finally
            {
                scope = scope.getParent();
            }

        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        Environment.PlcObject returnValue = visit(ast.getValue());

        throw new Return(returnValue);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        Environment.PlcObject ob = null;
        if(ast.getLiteral() == (null)){
            ob = Environment.create(Environment.NIL.getValue());
        }
        else {
            ob = Environment.create(ast.getLiteral());
        }
        //Environment.PlcObject ob = new Environment.PlcObject(scope, ast.getLiteral());
        return ob;
    }


    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast)
    {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast)
    {
        //get the operator (+, -, &&) from the AST node
        String operator = ast.getOperator();

        //calculate the left side expression
        Environment.PlcObject left = visit(ast.getLeft());
        //temp to hold the right
        Environment.PlcObject right;

        switch (operator)
        {
            //and - left cant be false
            case "&&":
                if (!(Boolean) left.getValue()) return Environment.create(false);
                right = visit(ast.getRight());
                return Environment.create((Boolean) left.getValue() && (Boolean) right.getValue());

            //or - left cant be false
            case "||":
                if ((Boolean) left.getValue()) return Environment.create(true);
                right = visit(ast.getRight());
                return Environment.create((Boolean) left.getValue() || (Boolean) right.getValue());

            //equality
            case "==":
                right = visit(ast.getRight());
                return Environment.create(Objects.equals(left.getValue(), right.getValue()));

            //inequality
            case "!=":
                right = visit(ast.getRight());
                return Environment.create(!Objects.equals(left.getValue(), right.getValue()));

            //addition - needs string concatenation and number addition
            case "+":
                right = visit(ast.getRight());

                //string concatenation if either operand is a string
                if (left.getValue() instanceof String || right.getValue() instanceof String)
                    return Environment.create(left.getValue().toString() + right.getValue().toString());
                    //addition for BigIntegers
                else if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger)
                    return Environment.create(((BigInteger) left.getValue()).add((BigInteger) right.getValue()));

                    //addition for BigDecimals
                else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal)
                    return Environment.create(((BigDecimal) left.getValue()).add((BigDecimal) right.getValue()));
                else
                    throw new RuntimeException("Invalid operands for '+' operation");

            case "-":
                right = visit(ast.getRight());
                //if both are BigIntegers, subtract
                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger)
                    return Environment.create(((BigInteger) left.getValue()).subtract((BigInteger) right.getValue()));

                    //if both are BigDecimals, subtract
                else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal)
                    return Environment.create(((BigDecimal) left.getValue()).subtract((BigDecimal) right.getValue()));
                else
                    //throw if operands are incompatible for subtraction
                    throw new RuntimeException("Invalid operands for '-' operation");

            case "*":
                right = visit(ast.getRight());

                //if both are BigIntegers, multiply
                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger)
                    return Environment.create(((BigInteger) left.getValue()).multiply((BigInteger) right.getValue()));

                    //if both are BigDecimals, multiply
                else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal)
                    return Environment.create(((BigDecimal) left.getValue()).multiply((BigDecimal) right.getValue()));
                else
                    //throw if operands are incompatible for multiply
                    throw new RuntimeException("Invalid operands for '*' operation");

            case "/":
                right = visit(ast.getRight());

                //check if operands are of numbers and division by zero
                if ((left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) ||
                        (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal))
                {
                    if (right.getValue().equals(BigInteger.ZERO) || right.getValue().equals(BigDecimal.ZERO))
                        //no divide by 0
                        throw new RuntimeException("Division by zero");

                    //if both are BigIntegers, divide
                    if (left.getValue() instanceof BigInteger)
                        return Environment.create(((BigInteger) left.getValue()).divide((BigInteger) right.getValue()));

                        //if both are BigDecimals, divide using HALF_EVEN rounding
                    else
                        return Environment.create(((BigDecimal) left.getValue()).divide((BigDecimal) right.getValue(), RoundingMode.HALF_EVEN));

                }
                else
                    //throw if operands are incompatible for dividing
                    throw new RuntimeException("Invalid operands for '/' operation");


                //power - only for BigInteger
            case "^":
                right = visit(ast.getRight());
                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger)
                    return Environment.create(((BigInteger) left.getValue()).pow(((BigInteger) right.getValue()).intValueExact()));
                else
                    throw new RuntimeException("Invalid operands for '^' operation");


                //comparison
            case "<":
            case ">":
                right = visit(ast.getRight());
                //check if both sides are comparable and in same class
                if (left.getValue() instanceof Comparable && right.getValue().getClass().equals(left.getValue().getClass()))
                {
                    Comparable leftComparable = (Comparable) left.getValue();
                    Comparable rightComparable = (Comparable) right.getValue();

                    //ternary conditional based on operator
                    boolean result = operator.equals("<") ? leftComparable.compareTo(rightComparable) < 0 : leftComparable.compareTo(rightComparable) > 0;
                    return Environment.create(result);
                }
                else
                    throw new RuntimeException("Operands for '" + operator + "' must be Comparable and of the same type.");

                //if the operator is not supported, throw
            default:
                throw new RuntimeException("Unsupported operator: " + operator);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Environment.PlcObject visit(Ast.Expression.Access ast) {

        Ast.Expression.Access ax = ast;
        if(ax.getOffset().equals(Optional.empty())) {
            Environment.Variable var = scope.lookupVariable(ax.getName());
            return var.getValue();
        }
        else{
            Ast.Expression.Literal offset = (Ast.Expression.Literal) ax.getOffset().get();
            String strlit = offset.getLiteral().toString();
            int lit = Integer.parseInt(strlit);
            Environment.Variable var = scope.lookupVariable(ax.getName());
            List<Object> list;
            list = (List<Object>) var.getValue().getValue();

            Object value = list.get(lit);
            Environment.PlcObject ob = new Environment.PlcObject(scope,value);
            return ob;

        }

    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        List<Ast.Expression> args= ast.getArguments();
        List<Environment.PlcObject> obs = new ArrayList<>();
        for(int i = 0; i<args.size(); i++) {
            Object x = visit(args.get(i)).getValue();
            Environment.PlcObject ob = Environment.create(x);
            obs.add(ob);
        }
        Environment.Function fun = scope.lookupFunction(ast.getName(), args.size());
        return fun.invoke(obs);

    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        List<Ast.Expression> obs = ast.getValues();
        List<Object> lits = new ArrayList<>();
        for (int i = 0; i<obs.size(); i++) {
            Ast.Expression.Literal lit = (Ast.Expression.Literal) obs.get(i);
            Object lit2 = lit.getLiteral();
            lits.add(lit2);
        }
        Environment.PlcObject ret = Environment.create(lits);
        return ret;
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
