package plc.project;

import java.beans.Expression;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        newline(0);
        indent++;
        List<Ast.Global> globals = ast.getGlobals();
        for (int i = 0; i<globals.size(); i++){
            newline(indent);
            visit(globals.get(i));
            if(i == globals.size()-1){
                newline(0);
            }
        }
        newline(indent);

        print("public static void main(String[] args) {");
        indent++;
        newline(indent);
        print("System.exit(new Main().main());");
        indent--;
        newline(indent);
        print("}");
        newline(0);
        List<Ast.Function> functions = ast.getFunctions();
        for (int i = 0; i<functions.size(); i++){
            newline(indent);
            visit(functions.get(i));
            newline(0);
        }
        //newline(0);
        indent--;
        newline(indent);
        print("}");




        //Ast.Function fun = ast.getFunctions().get(0);
        //visit(fun);
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if(ast.getValue().isPresent())
        {

            String name = ast.getName();
            Class val = ast.getValue().get().getClass();
            boolean mutable = ast.getMutable();

            String typename = ast.getTypeName();
            typename = converter(typename);
            if(val.getName().equals("plc.project.Ast$Expression$PlcList")){
                typename = typename+"[]";
            }
            if(!mutable){
                print("final ");
            }
            Ast.Expression var = ast.getValue().get();
            print(typename, " ", name, " = ");
            print(var, ";");

        }
        else
        {
            String name = ast.getName();
            boolean mutable = ast.getMutable();
            String typename = ast.getTypeName();
            typename = converter(typename);
            if(!mutable){
                print("final ");
            }
            print(typename, " ", name, ";");

        }
        return null;
    }

    public String converter(Object x) {
        String str = x.toString().toUpperCase();
        switch (str) {
            case "ANY":
                str = "Object";
                break;
            case "NIL":
                str = "null";
                break;
            case "COMPARABLE":
                str = "Comparable";
                break;
            case "BOOLEAN":
                str = "boolean";
                break;
            case "INTEGER":
                str = "int";
                break;
            case "DECIMAL":
                str = "double"; // already handled
                break;
            case "CHARACTER":
                str = "char";
                break;
            case "STRING":
                str = "String";
                break;
            default:
                str = "Unknown Type";
                break;
        }
        return str;
    }

    @Override
    public Void visit(Ast.Function ast) {
        Object returntype = "";
        boolean hasreturn = false;
        try {
            returntype = ast.getReturnTypeName().get();
            returntype = converter(returntype);
        }
        catch(RuntimeException e){
            returntype = "void";
        }
        Object name = ast.getName();
        List<String> parameternames = ast.getParameters();
        List<String> parametertypenames = ast.getParameterTypeNames();


        print(returntype, " ", name);
        print("(");
        for(int i = 0; i<parameternames.size(); i++){
            String type = parametertypenames.get(i);
            type = converter(type);

            String parname = parameternames.get(i);
            if(i==parameternames.size()-1){
                print(type," ", parname);
            }
            else {
                print(type," ", parname, ", ");
            }
        }
        print(")", " ", "{");
        indent ++;
        newline(indent);

        List<Ast.Statement> statements = ast.getStatements();
        for(int i = 0; i<statements.size(); i++){
            Ast.Statement statement = statements.get(i);
            if(statement.getClass().getName().equals("plc.project.Ast$Statement$Return")){
                hasreturn = true;
            }
            visit(statement);
            if(!(i == statements.size()-1)) {
                newline(indent);
            }
        }

        if(name.toString().equals("main") && !hasreturn){
            if(!(statements.size()==0)){
                newline(indent);
            }
            print("return 0;");
        }

        indent--;
        newline(indent);
        print("}");





        return null;


    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        Ast.Expression exp = ast.getExpression();
        Ast.Expression.Function fun;
        try{
            fun = (Ast.Expression.Function) exp;
            String name = fun.getName();
            List<Ast.Expression> args = fun.getArguments();
            if(name.equals("print")){
                name = "System.out.println";
            }
            print(name, "(");
            for(int i=0; i<args.size(); i++) {
                print(args.get(i));
            }
            print(");");
        }
        catch (ClassCastException e){
            print(ast.getExpression());
            print(";");
        }

        return null;



    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        if(ast.getValue().isPresent())
        {
            String name = ast.getName();
            String typename;
            try {
                typename = ast.getTypeName().get();
            }
            catch (RuntimeException e){
                typename = ast.getVariable().getType().getName();
            }
            typename = converter(typename);
            Ast.Expression var = ast.getValue().get();
            print(typename, " ", name, " = ");
            print(var, ";");
        }
        else
        {
            String name = ast.getName();
            String typename;

            try {
                typename = ast.getTypeName().get();
            }
            catch (RuntimeException e){
                typename = "Any";
            }
            //String typename = String.valueOf(ast.getTypeName());
            typename = converter(typename);
            print(typename, " ", name, ";");
        }
        return null;

    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        Ast.Expression expression = ast.getValue();
        Ast.Expression reciever = ast.getReceiver();
        print(reciever, " = ", expression, ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (");
        visit(ast.getCondition());
        print(") {");
        indent++;
        List<Ast.Statement> stat = ast.getThenStatements();

        for (Ast.Statement statement : stat)
        {
            newline(indent);
            visit(statement);
        }
        //print(";");
        indent--;
        newline(indent);
        print("}");
        if (!ast.getElseStatements().isEmpty())
        {
            print(" else {");
            indent++;
            for (Ast.Statement statement : ast.getElseStatements())
            {
                newline(indent);
                visit(statement);
            }
            //print(";");
            indent--;
            newline(indent);
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast)
    {
        print("switch (");
        visit(ast.getCondition());
        print(") {");
        indent++;

        for (Ast.Statement.Case caseStmt : ast.getCases())
        {
            newline(indent);
            visit(caseStmt);
        }

        newline(indent - 1);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast)
    {
        if (ast.getValue().isPresent())
        {
            print("case ");
            visit(ast.getValue().get());
            print(":");
            newline(++indent);
            for (Ast.Statement statement : ast.getStatements())
            {
                visit(statement);
                newline(indent);
            }

            if (!ast.getStatements().isEmpty())
                print("break;");

            --indent;
            //if (!ast.getStatements().isEmpty())
            //    newline(indent);
        }
        else
        {
            print("default:");
            newline(++indent);
            for (Ast.Statement statement : ast.getStatements())
            {
                visit(statement);
                if (ast.getStatements().indexOf(statement) < ast.getStatements().size() - 1)
                    newline(indent);
            }
            --indent;
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast)
    {
        print("while (");
        visit(ast.getCondition());
        print(") {");
        indent++;
        List<Ast.Statement> stats = ast.getStatements();
        for (Ast.Statement statement : stats)
        {
            newline(indent);
            visit(statement);
        }
        indent--;
        if(!(stats.isEmpty())){
            newline(indent);
            }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        Ast.Expression ret = ast.getValue();
        print("return ");
        visit(ret);
        print(";");
        return null;
    }

    public static String escapeString(String input) {
        input = input.replaceAll("\\\\", "\\\\\\\\");


        input = input.replaceAll("\\n", "\\\\n");
        input = input.replaceAll("\\t", "\\\\t");
        input = input.replaceAll("\\r", "\\\\r");
        input = input.replaceAll("\\\"", "\\\\\"");

        return input;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object lit = ast.getLiteral();
        try {
            Environment.Type t = ast.getType();
            if (t.getName().equals("String")) {
                String literal = (String) lit;
                literal = escapeString(literal);
                lit = "\"" + literal + "\"";
            }
            else if (t.getName().equals("Character")) {
                lit = "\'" + lit + "\'";
            }
            print(lit);
        }
        catch (IllegalStateException e){
            print(lit);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        Ast.Expression expression = ast.getExpression();
        print("(",expression,")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        Ast.Expression leftexp = ast.getLeft();
        Ast.Expression rightexp = ast.getRight();
        String oper = ast.getOperator();
        if(oper.equals("^")){
            print("Math.pow(");
            print(leftexp);
            print(", ");
            print(rightexp);
            print(")");
        }
        else{
            print(leftexp);
            print(" ",oper," ");
            print(rightexp);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {

        String name = ast.getName();
        Optional off = ast.getOffset();
        print(name);
        if(off.isPresent()) {
            Object offset = off.get();
            print("[",offset,"]");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        String name = ast.getFunction().getJvmName();
        List<Ast.Expression> args = ast.getArguments();
        if(name.equals("print")){
            name = "System.out.println";
        }
        print(name, "(");
        for(int i=0; i<args.size(); i++) {
            print(args.get(i));
        }
        print(")");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        List<Ast.Expression> exps = ast.getValues();
        print("{");
        for(int i = 0; i< exps.size(); i++){
            print(exps.get(i));
            if(i!=exps.size()-1){
                print(", ");
            }
        }
        print("}");
        return null;
    }

}
