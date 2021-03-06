package com.cliffc.aa;

import com.cliffc.aa.type.*;
import com.cliffc.aa.util.Bits;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestType {
  @Test public void testType0() {
    // User-defined linked-list
    //test("List=:@{next,val}; x=List(0,\"abc\"); x.val", TypeStr.ABC);
    //test("List=:@{next,val}; map = { f list -> list ? List(@{next=map(f,list.next),val=f(list.val)}) : 0 }",TypeInt.TRUE);


    // Simple int
    test("1",   TypeInt.TRUE);
    // Unary operator
    test("-1",  TypeInt.con( -1));
    test("!1",  TypeInt.con(  0));
    // Binary operators
    test("1+2", TypeInt.con(  3));
    test("1-2", TypeInt.con( -1));
    test("1+2*3", TypeInt.con(  7));
    test("1  < 2", TypeInt.TRUE );
    test("1  <=2", TypeInt.TRUE );
    test("1  > 2", TypeInt.FALSE);
    test("1  >=2", TypeInt.FALSE);
    test("1  ==2", TypeInt.FALSE);
    test("1  !=2", TypeInt.TRUE );
    test("1.2< 2", TypeInt.TRUE );
    test("1.2<=2", TypeInt.TRUE );
    test("1.2> 2", TypeInt.FALSE);
    test("1.2>=2", TypeInt.FALSE);
    test("1.2==2", TypeInt.FALSE);
    test("1.2!=2", TypeInt.TRUE );

    // Binary with precedence check
    test(" 1+2 * 3+4 *5", TypeInt.con( 27));
    test("(1+2)*(3+4)*5", TypeInt.con(105));
    test("1// some comment\n+2", TypeInt.con(3)); // With bad comment

    // Float
    test("1.2+3.4", TypeFlt.make(0,64,4.6));
    // Mixed int/float with conversion
    test("1+2.3",   TypeFlt.make(0,64,3.3));
  
    // Simple strings
    test("\"Hello, world\"", TypeStr.con("Hello, world"));
    test("str(3.14)", TypeStr.con("3.14"));
    test("str(3)", TypeStr.con("3"));
    test("str(\"abc\")", TypeStr.ABC);

    // Variable lookup
    test("math_pi", TypeFlt.PI);
    // bare function lookup; returns a union of '+' functions
    testerr("+", "Syntax error; trailing junk","");
    test("{+}", Env.lookup_valtype("+"));
    test("!", Env.lookup_valtype("!")); // uniops are just like normal functions
    // Function application, traditional paren/comma args
    test("{+}(1,2)", TypeInt.con( 3));
    test("{-}(1,2)", TypeInt.con(-1)); // binary version
    test(" - (1  )", TypeInt.con(-1)); // unary version
    // error; mismatch arg count
    testerr("!()       ", "Passing 0 arguments to !{int64 -> int1} which takes 1 arguments","   ");
    testerr("math_pi(1)", "A function is being called, but 3.141592653589793 is not a function type","          ");
    testerr("{+}(1,2,3)", "Passing 3 arguments to +{flt64 flt64 -> flt64} which takes 2 arguments","          ");

    // Parsed as +(1,(2*3))
    test("{+}(1, 2 * 3) ", TypeInt.con(7));
    // Parsed as +( (1+2*3) , (4*5+6) )
    test("{+}(1 + 2 * 3, 4 * 5 + 6) ", TypeInt.con(33));
    // Statements
    test("(1;2 )", TypeInt.con(2));
    test("(1;2;)", TypeInt.con(2)); // final semicolon is optional
    test("{+}(1;2 ,3)", TypeInt.con(5)); // statements in arguments
    test("{+}(1;2;,3)", TypeInt.con(5)); // statements in arguments

    // Syntax for variable assignment
    test("x=1", TypeInt.TRUE);
    test("x=y=1", TypeInt.TRUE);
    testerr("x=y=", "Missing ifex after assignment of 'y'","    ");
    testerr("x=y" , "Unknown ref 'y'","   ");
    testerr("x=1+y","Unknown ref 'y'","     ");
    test("x=2; y=x+1; x*y", TypeInt.con(6));
    // Re-use ref immediately after def; parses as: x=(2*3); 1+x+x*x
    test("1+(x=2*3)+x*x", TypeInt.con(1+6+6*6));
    testerr("x=(1+(x=2)+x)", "Cannot re-assign val 'x'","             ");

    // Conditional:
    test   ("0 ?    2  : 3", TypeInt.con(3)); // false
    test   ("2 ?    2  : 3", TypeInt.con(2)); // true
    test   ("math_rand(1)?(x=4):(x=3);x", TypeInt.INT8); // x defined on both arms, so available after
    test   ("math_rand(1)?(x=2):   3 ;4", TypeInt.con(4)); // x-defined on 1 side only, but not used thereafter
    test   ("math_rand(1)?(y=2;x=y*y):(x=3);x", TypeInt.INT8); // x defined on both arms, so available after, while y is not
    testerr("math_rand(1)?(x=2):   3 ;x", "'x' not defined on false arm of trinary","                        ");
    testerr("math_rand(1)?(x=2):   3 ;y=x+2;y", "'x' not defined on false arm of trinary","                        ");
    testerr("0 ? (x=2) : 3;x", "'x' not defined on false arm of trinary","             ");
    test   ("2 ? (x=2) : 3;x", TypeInt.con(2)); // off-side is constant-dead, so missing x-assign is ignored
    test   ("2 ? (x=2) : y  ", TypeInt.con(2)); // off-side is constant-dead, so missing 'y'      is ignored
    testerr("x=1;2?(x=2):(x=3);x", "Cannot re-assign val 'x'","          ");
    test   ("x=1;2?   2 :(x=3);x",TypeInt.con(1)); // Re-assigned allowed & ignored in dead branch
    test   ("math_rand(1)?1:int:2:int",TypeInt.INT8); // no ambiguity between conditionals and type annotations
    testerr("math_rand(1)?1: :2:int","missing expr after ':'","                "); // missing type
    testerr("math_rand(1)?1::2:int","missing expr after ':'","               "); // missing type
    testerr("math_rand(1)?1:\"a\"", "Cannot mix GC and non-GC types", "                  " );

    // Anonymous function definition
    test_isa("{x y -> x+y}", TypeTuple.FUNPTR2); // actually {Flt,Int} x {FltxInt} -> {FltxInt} but currently types {SCALAR,SCALAR->SCALAR}
    test("{5}()", TypeInt.con(5)); // No args nor -> required; this is simply a function returning 5, being executed

    // ID in different contexts; in general requires a new TypeVar per use; for
    // such a small function it is always inlined completely, has the same effect.
    test("id", Env.lookup_valtype("id"));
    test("id(1)",TypeInt.con(1));
    test("id(3.14)",TypeFlt.con(3.14));
    test("id({+})",Env.lookup_valtype("+")); // 
    test("id({+})(id(1),id(math_pi))",TypeFlt.make(0,64,Math.PI+1));

    // Function execution and result typing
    test("x=3; andx={y -> x & y}; andx(2)", TypeInt.con(2)); // trivially inlined; capture external variable
    test("x=3; and2={x -> x & 2}; and2(x)", TypeInt.con(2)); // trivially inlined; shadow  external variable
    testerr("plus2={x -> x+2}; x", "Unknown ref 'x'","                   "); // Scope exit ends lifetime
    testerr("fun={x -> }", "Missing function body","          ");
    testerr("fun(2)", "Unknown ref 'fun'", "   ");
    test("mul3={x -> y=3; x*y}; mul3(2)", TypeInt.con(6)); // multiple statements in func body
    // Needs overload cloning/inlining to resolve {+}
    test("x=3; addx={y -> x+y}; addx(2)", TypeInt.con(5)); // must inline to resolve overload {+}:Int
    test("x=3; mul2={x -> x*2}; mul2(2.1)", TypeFlt.con(2.1*2.0)); // must inline to resolve overload {*}:Flt with I->F conversion
    test("x=3; mul2={x -> x*2}; mul2(2.1)+mul2(x)", TypeFlt.con(2.1*2.0+3*2)); // Mix of types to mul2(), mix of {*} operators
    test("sq={x -> x*x}; sq 2.1", TypeFlt.con(4.41)); // No () required for single args

    // Type annotations
    test("-1:int", TypeInt.con( -1));
    test("(1+2.3):flt", TypeFlt.make(0,64,3.3));
    test("x:int = 1", TypeInt.TRUE);
    test("x:flt = 1", TypeInt.TRUE); // casts for free to a float
    testerr("x:flt32 = 123456789", "123456789 is not a flt32","                   ");
    testerr("1:","Syntax error; trailing junk"," "); // missing type
    testerr("2:x", "Syntax error; trailing junk", " ");
    testerr("(2:)", "Syntax error; trailing junk", "  ");

    test   (" -1 :int1", TypeInt.con(-1));
    testerr("(-1):int1", "-1 is not a int1","         ");
    testerr("\"abc\":int", "\"abc\" is not a int64","         ");
    testerr("1:str", "1 is not a str","     ");

    testerr("x=3; fun:{int->int}={x -> x*2}; fun(2.1)+fun(x)", "2.1 is not a int64","                              ");
    test("x=3; fun:{real->real}={x -> x*2}; fun(2.1)+fun(x)", TypeFlt.con(2.1*2+3*2)); // Mix of types to fun()
    test("fun:{real->flt32}={x -> x}; fun(123 )", TypeInt.con(123 ));
    test("fun:{real->flt32}={x -> x}; fun(0.125)", TypeFlt.con(0.125));
    testerr("fun:{real->flt32}={x -> x}; fun(123456789)", "123456789 is not a flt32","                          ");

    test   ("{x:int -> x*2}(1)", TypeInt.con(2)); // Types on parms
    testerr("{x:str -> x}(1)", "1 is not a str", "  ");

    // Recursive:
    test("fact = { x -> x <= 1 ? x : x*fact(x-1) }; fact(3)",TypeInt.con(6));
    test("fib = { x -> x <= 1 ? 1 : fib(x-1)+fib(x-2) }; fib(4)",TypeInt.INT64);

    // Co-recursion requires parallel assignment & type inference across a lexical scope
    test("is_even = { n -> n ? is_odd(n-1) : 1}; is_odd = {n -> n ? is_even(n-1) : 0}; is_even(4)", TypeInt.TRUE );
    test("is_even = { n -> n ? is_odd(n-1) : 1}; is_odd = {n -> n ? is_even(n-1) : 0}; is_even(5)", TypeUnion.NIL);

    // simple anon struct tests
    test   ("  @{x,y} ", TypeStruct.makeA(new String[]{"x","y"},TypeErr.ANY,TypeErr.ANY)); // simple anon struct decl
    testerr("a=@{x=1.2,y}; x", "Unknown ref 'x'","               ");
    testerr("a=@{x=1,x=2}", "Cannot define field '.x' twice","           ");
    test   ("a=@{x=1.2,y,}; a.x", TypeFlt.con(1.2)); // standard "." field naming; trailing comma optional
    testerr("(a=@{x,y}; a.)", "Missing field name after '.'","             ");
    testerr("a=@{x,y}; a.x=1","Cannot re-assign field '.x'","               ");
    test   ("a=@{x=0,y=1}; b=@{x=2}  ; c=math_rand(1)?a:b; c.x", TypeInt.INT8); // either 0 or 2; structs can be partially merged
    testerr("a=@{x=0,y=1}; b=@{x=2}; c=math_rand(1)?a:b; c.y",  "Unknown field '.y'","                                               ");
    testerr("dist={p->p.x*p.x+p.y*p.y}; dist(@{x=1})", "Unknown field '.y'","                    ");
    test   ("dist={p->p.x*p.x+p.y*p.y}; dist(@{x=1,y=2})", TypeInt.con(5));     // passed in to func
    test   ("dist={p->p.x*p.x+p.y*p.y}; dist(@{x=1,y=2,z=3})", TypeInt.con(5)); // extra fields OK
    test   ("dist={p:@{x,y} -> p.x*p.x+p.y*p.y}; dist(@{x=1,y=2})", TypeInt.con(5)); // Typed func arg
    test   ("a=@{x=(b=1.2)*b,y=b}; a.y", TypeFlt.con(1.2 )); // ok to use temp defs
    test   ("a=@{x=(b=1.2)*b,y=x}; a.y", TypeFlt.con(1.44)); // ok to use early fields in later defs
    testerr("a=@{x=(b=1.2)*b,y=b}; b", "Unknown ref 'b'","                       ");

    test   ("dist={p->p//qqq\n.//qqq\nx*p.x+p.y*p.y}; dist(//qqq\n@{x//qqq\n=1,y=2})", TypeInt.con(5));

    // Tuple
    test("(0,\"abc\")", TypeTuple.make_all(TypeUnion.NIL,TypeStr.ABC));
    
    // Named type variables
    test_isa("gal=:flt"       , TypeTuple.make_fun_ptr(TypeFun.make(TypeTuple.FLT64,TypeName.make("gal",TypeFlt.FLT64),Bits.FULL)));
    test_isa("gal=:flt; gal"  , TypeTuple.make_fun_ptr(TypeFun.make(TypeTuple.FLT64,TypeName.make("gal",TypeFlt.FLT64),Bits.FULL)));
    test    ("gal=:flt; 3==gal(2)+1", TypeInt.TRUE);
    test    ("gal=:flt; tank:gal = gal(2)", TypeName.make("gal",TypeFlt.con(2)));
    // test    ("gal=:flt; tank:gal = 2.0", TypeName.make("gal",TypeFlt.con(2))); // TODO: figure out if free cast for bare constants?
    testerr ("gal=:flt; tank:gal = gal(2)+1", "3.0 is not a gal:flt64","                             ");
    test    ("Point=:@{x,y}; dist={p:Point -> p.x*p.x+p.y*p.y}; dist(Point(@{x=1,y=2}))", TypeInt.con(5));
    test    ("Point=:@{x,y}; dist={p       -> p.x*p.x+p.y*p.y}; dist(Point(@{x=1,y=2}))", TypeInt.con(5));
    testerr ("Point=:@{x,y}; dist={p:Point -> p.x*p.x+p.y*p.y}; dist(     (@{x=1,y=2}))", "@{x:1,y:2,} is not a Point:@{x,y,}","                      ");

    // nullable and not-null pointers
    test   ("x:str? = 0", TypeUnion.NIL); // question-type allows null or not; zero digit is null
    test   ("x:str? = \"abc\"", TypeStr.ABC); // question-type allows null or not
    testerr("x:str  = 0", "nil is not a str", "          ");
    test   ("math_rand(1)?0:\"abc\"", TypeStr.STR0);
    testerr("(math_rand(1)?0 : @{x=1}).x", "Struct might be nil when reading field '.x'", "                           ");
    test   ("p=math_rand(1)?0:@{x=1}; p ? p.x : 0", TypeInt.BOOL); // not-null-ness after a null-check
    test   ("x:int = y:str? = z:flt = 0", TypeUnion.NIL); // null/0 freely recasts
    test   ("\"abc\"==0", TypeInt.FALSE ); // No type error, just not null
    test   ("\"abc\"!=0", TypeInt.TRUE  ); // No type error, just not null
    test   ("nil=0; \"abc\"!=nil", TypeInt.TRUE); // Another way to name null
    test   ("a = math_rand(1) ? 0 : @{x=1}; // a is null or a struct\n"+
            "b = math_rand(1) ? 0 : @{c=a}; // b is null or a struct\n"+
            "b ? (b.c ? b.c.x : 0) : 0      // Null-safe field load", TypeInt.BOOL); // Nested null-safe field load

    // User-defined linked list
    test("List=:@{next,val}; x=List(@{next=0,val=\"abc\"}); x.val", TypeStr.ABC);

    // TODO: Need real TypeVars for these
    //test("id:{A->A}"    , Env.lookup_valtype("id"));
    //test("id:{A:int->A}", Env.lookup_valtype("id"));
    //test("id:{int->int}", Env.lookup_valtype("id"));
  }

  /*

// A tuple of null and a string
list_of_hello = @{ 0, "hello", }
list_of_hello =  ( 0, "hello", )

TODO: Decide if calls only ever take 1 arg, and also args auto-gather into
tuples which is the one arg passed into a call.  Eg: dist(x,y) parses into a
function 'dist' and a 2-element tuple '(x,y)' and then a function call of the
one tuple arg.  Meanwhile dist is declared as 'dist = { x y -> ...}  which is
syntax sugar for naming the elements of the 1 tuple arg passed in.

The difference shows up in any sort of varargs-like syntax, where multiple
different arguments get passed in.


// No ambiguity:
 { x } // no-arg-function returning external variable x; same as { -> x }
 { x } // 1-elem tuple     wrapping external variable x
@{ x } // 1-elem struct type with field named x

// type variables are free in : type expressions

// Define a pair as 2 fields "a" and "b" both with the same type T.  Note that
// 'a' and 'b' and 'T' are all free, but the @ parses this as a struct, so 'a'
// and 'b' become field names and 'T' becomes a free type-var.
Pair = :@{ a:T, b:T }

// Since 'A' and 'B' are free and not field names, they become type-vars.
MapType = :{ {A->B} List(A) -> List(B) }

// map: no leading ':' so a function definition, not a type def
map:{ {A->B} List(A) -> List(B) }  = { f list -> ... }

// A List type.  Named types are not 'null', so not valid to use "List = :0|...".
// Type List takes a type-variable 'A' (which is free in the type expr).
// List is a self-recursive type.
// Field 'next' can be null or List(A).
// Field 'val' is type A.
List = :@{ next:List(A)?, val:A }

list = @{ next:List(A)?, val:A }

List = :@{ next, val }
head = { list -> list.val  } // fails to compile if list is nil
tail = { list -> list.next } // fails to compile if list is nil

map = { f list -> list ? List(@{map(f,list.next),f(list.val)}) : 0 }

// Type A can allow nulls, or not
strs:List(0)    = ... // List of nulls
strs:List(str)  = ... // List of not-null strings
strs:List(str?) = ... // List of null-or-strings

// TODO: Re-assignment; ':=' allows more ':=' or exactly 1 final assignment '='
x := 1; x:= 2; x = 3; x // 3
x  = 1; x = 2; // cannot reassign
x  = 1; x:= 2; // cannot reassign
x := 1; rand ? x =2 :  3; x; // cannot partially final-assign
x := 1; rand ? x:=2 :  3; x; // 2; x is still assignable
x := 1; rand ? x =2 :x=3; x; // 2or3; x is final
x := 1; x = x; // 1; make x final

// With re-assignment, more excitement around LHS!
// So fields in a tuple type have a init-value, a final-value, an
// un-init-value, a mixed-init-value, and a name
make_point={{x,y}} // returns {x,y} with both un-init
a=make_point(); a.x=1; // a.x init; a.y uninit
b=make_point(); b.y=2; // a.x uninit; b.y init
c = rand ? a : b;      // c: worse-case x & y mixed init & uninit
c.x = 1; // Error; might be    init
c.x;     // Error; might be un-init
// reflection read/write of fields.
// '[' binary operator returns a LHS value (really a 2-tuple).
// ']' postfix operator takes a LHS, returns value
// ']=' binary operator takes a LHS and a value, and returns same value... and SIDE-EFFECTS
c[x];
c[x]=1;

   */
  
  static private void test( String program, Type expected ) {
    TypeEnv te = Exec.go("args",program);
    if( te._errs != null ) System.err.println(te._errs.toString());
    Assert.assertNull(te._errs);
    assertEquals(expected,te._t);
  }
  static private void test_isa( String program, Type expected ) {
    TypeEnv te = Exec.go("args",program);
    if( te._errs != null ) System.err.println(te._errs.toString());
    Assert.assertNull(te._errs);
    assertTrue(te._t.isa(expected));
  }
  static private void testerr( String program, String err, String cursor ) {
    String err2 = "\nargs:0:"+err+"\n"+program+"\n"+cursor+"^\n";
    TypeEnv te = Exec.go("args",program);
    assertTrue(te._errs != null && te._errs._len>=1);
    assertEquals(err2,te._errs.at(0));
  }

  @Test public void testNamesInts() {
    Type.init0(new HashMap<>());

    // Lattice around int8 and 0 is well formed; exactly 3 edges, 3 nodes
    // Confirm lattice: {~i16 -> ~i8 -> 0 -> i8 -> i16 }
    // Confirm lattice: {        ~i8 -> 1 -> i8        }
    Type  i16= TypeInt.INT16;
    Type  i8 = TypeInt.INT8;
    Type xi8 = i8.dual();
    Type xi16= i16.dual();
    Type z   = TypeInt.FALSE;
    Type o   = TypeInt.TRUE;
    assertEquals(xi8,xi8.meet(xi16)); // ~i16-> ~i8
    assertEquals( z ,z  .meet(xi8 )); // ~i8 ->  0
    assertEquals(i8 ,i8 .meet(z   )); //  0  -> i8
    assertEquals(i16,i16.meet(xi8 )); //  i8 -> i16
    assertEquals( o ,o  .meet(xi8 )); // ~i8 ->  1
    assertEquals(i8 ,i8 .meet(o   )); //  1  -> i8

    // Lattice around n:int8 and n:0 is well formed; exactly 2 edges, 3 nodes
    // Confirm lattice: {N:~i8 -> N:1 -> N:i8}
    // Confirm lattice: {N:~i8 ->   0 -> N:i8}
    Type ni8 = TypeName.TEST_ENUM;
    Type xni8= ni8.dual(); // dual name:int8
    Type no  = TypeName.make("__test_enum",o);
    Type nz  = TypeName.make("__test_enum",z);
    assertEquals(no ,no .meet(xni8)); // N:~i8 -> N: 1
    assertEquals(ni8,ni8.meet(no  )); // N:  1 -> N:i8
    assertEquals(ni8,ni8.meet(nz  )); //   N:0 -> N:i8
    assertEquals(nz ,nz .meet(xni8)); // N:~i8 -> N:0
    assertEquals(no ,no .meet(xni8)); // 1 & n:~i8 -> mixing 0 and 1

    // Crossing lattice between named and unnamed ints
    //      Confirm lattice: {~i8 -> N:~i8 -> 0 -> N:i8 -> i8; N:0 -> 0 }
    // NOT: Confirm lattice: {N:~i8 -> ~i8; N:i8 -> i8 }
    assertEquals(xni8,xni8.meet( xi8));//   ~i8 -> N:~i8
    assertEquals(TypeInt.BOOL, z  .meet(xni8));// N:~i8 -> {0,1}??? When falling off from a Named Int, must fall below ANY constant to keep a true lattice
    assertEquals(  i8, ni8.meet(   z));//     0 -> N:i8
    assertEquals(  i8,  i8.meet( ni8));// N: i8 ->   i8

    assertEquals(xni8,xi8.meet(xni8));
    assertEquals(TypeInt.BOOL, o .meet(xni8));
  }
  
  // oop? -> {str?,tup?} -> { null, string constants, tuple constants } -> {~str+?,~tup+?} -> ~oop+?
  // Notice multiple NILs; can be many for each type.
  @Test public void testOOPsNulls() {
    Type.init0(new HashMap<>());
    Type bot  = TypeErr  .ALL;
    Type oop0 = TypeOop  .OOP0; // OOP? (OOP and null)
    Type str0 = TypeStr  .STR0; // str? (str AND null)
    Type str  = TypeStr  .STR;  // str no null
    Type tup0 = TypeTuple.ALL0; // tup? (tup AND null); infinite repeat of ALL fields
    Type tup  = TypeTuple.ALL;  // tup       no  null ; infinite repeat of ALL fields
    Type nilo = TypeOop  .NIL;
    Type nils = TypeStr  .NIL;
    Type nilt = TypeTuple.NIL;
    Type abc  = TypeStr  .ABC;
    Type fld  = TypeTuple.INT64;  // 1 field of int64
    
    Type tupx = tup .dual();      // ~tup   (choice tup, no NULL)
    Type tup_ = tup0.dual();      // ~tup+? (choice tup  OR NULL)
    Type strx = str .dual();      // ~str   (choice str, no NULL)
    Type str_ = str0.dual();      // ~str+? (choice str  OR NULL)
    Type oop_ = oop0.dual();      // ~OOP+? (choice OOP  OR null)
    Type top  = TypeErr.ANY;

    assertTrue(top .isa(oop_));

    assertTrue(oop_.isa(str_));
    assertTrue(oop_.isa(tup_));

    assertTrue(str_.isa(strx));
    assertTrue(tup_.isa(tupx));
    assertTrue(str_.isa(nilo));
    assertTrue(str_.isa(nils));
    assertTrue(tup_.isa(nilo));
    assertTrue(tup_.isa(nilt));

    assertTrue(strx.isa(abc ));
    assertTrue(tupx.isa(fld ));
    
    assertTrue(abc .isa(str ));
    assertTrue(fld .isa(tup ));
    assertTrue(nils.isa(str0));
    assertTrue(nilt.isa(tup0));

    assertTrue(str .isa(str0));
    assertTrue(tup .isa(tup0));

    assertTrue(str0.isa(oop0));
    assertTrue(tup0.isa(oop0));
    
    assertTrue(oop0.isa(bot ));
    
    // Crossing named:ints or named:null and OOP
    Type  i8 = TypeInt.INT8;
    Type xi8 = i8.dual();
    assertEquals( Type.SCALAR, xi8.meet(oop_)); // ~OOP+0 &   ~i8 -> 0
  }
  
  @Test public void testStructTuple() {
    Type.init0(new HashMap<>());
    Type nil  = TypeOop.NIL;
    // Tuple is more general that Struct
    Type tf = TypeTuple.FLT64; //  [  flt64,~Scalar...]; choice leading field name
    Type tsx= TypeStruct.X;    // @{x:flt64,~Scalar...}; fixed  leading field name
    Type tff = tsx.meet(tf);   //
    assertEquals(tsx,tff);     // tf.isa(tsx)
    TypeTuple t0 = TypeTuple.make_args(nil); //  [  0,~Scalar...]
    Type      ts0= TypeStruct.makeX(new String[]{"x"},nil);  // @{x:0,~Scalar...}
    Type tss = ts0.meet(t0);
    assertEquals(ts0,tss);      // t0.isa(ts0)

    // Union meets & joins same-class types
    Type uany = TypeUnion.make(true ,TypeInt.con(2),TypeInt.INT8);
    Type uall = TypeUnion.make(false,TypeInt.con(2),TypeInt.INT8);
    assertEquals(uany,TypeInt.con(2));
    assertEquals(uall,TypeInt.INT8  );
    
    // meet @{c:0}? and @{c:@{x:1}?,}
    Type nc0 = TypeStruct.make(TypeStruct.AND_NIL,new Type[]{nil},TypeErr.ALL,new String[]{"c"}); // @{c:0}?
    Type nx1 = TypeStruct.make(TypeStruct.AND_NIL,new Type[]{TypeInt.TRUE},TypeErr.ALL,new String[]{"x"}); // @{x:1}?
    Type cx  = TypeStruct.makeA(new String[]{"c"},nx1); // @{c:@{x:1}?}
    // JOIN tosses the top-level null choice, and the inside struct choice
    Type cj  = nc0.join(cx);
    Type c0  = TypeStruct.makeA(new String[]{"c"},((TypeNullable) nx1).make_nil((byte)0)); // @{c:0}
    assertEquals(c0,cj);
  }

  @Test public void testUnion() {
    Type.init0(new HashMap<>());

    Type a = TypeUnion.make(false,TypeInt.FALSE,TypeFlt.FLT32);
    assertEquals(TypeFlt.FLT32,a); // 0 isa FLT32, so collapses
    Type b = TypeUnion.make(false,TypeInt.con(123456789),TypeFlt.FLT32);
    assertEquals(Type.REAL,b); // Does not collapse
    Type c = TypeUnion.make(false,TypeInt.FALSE,TypeInt.TRUE);
    assertEquals(TypeInt.BOOL,c); // {0*1} combines to bool
    Type d = TypeUnion.make(false,TypeInt.FALSE,TypeOop.NIL);
    assertTrue(d instanceof TypeUnion); // Does not collapse

    Type nil = TypeUnion.NIL;
    Type e = TypeOop.NIL.meet(TypeUnion.NIL);
    assertEquals(TypeOop.NIL,e);
  }

  // TODO: Observation: value() calls need to be monotonic, can test this.
  @Test public void testCommuteSymmetricAssociative() {
    Type.init0(new HashMap<>());

    Type  n  = Type.NUM;
    Type xn  = Type.NUM.dual();
    Type onil= TypeOop.NIL;
    Type oop_= TypeOop.OOP_;
    Type  nil= TypeUnion.NIL;
    Type dnil= nil.dual();

    Type hi0 = xn.meet(dnil);
    assertEquals(dnil,hi0);
    Type mt2 = oop_.meet(n);
    assertEquals(Type.SCALAR,mt2);
    Type mt0 = xn.meet(onil);
    //assertEquals(nil,mt0);
    Type mt1 = onil.meet(nil);
    assertEquals(onil,mt1);
    
    assertTrue(Type.check_startup());
  }  
}
