package com.cburch.logisim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cburch.logisim.analyze.model.AlgebraSimplifier;
import com.cburch.logisim.analyze.model.AlgebraSimplifier.Law;
import com.cburch.logisim.analyze.model.AlgebraSimplifier.SimplificationStep;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.Parser;
import com.cburch.logisim.analyze.model.ParserException;
import com.cburch.logisim.analyze.model.Var;

public class SimplificationTest {
	
	private AnalyzerModel model;
	
	@BeforeEach
	public void setup() {
		// Creating input variables a,b,c and d
		// Output is always   x
		
		Var a = new Var("a",1);
		Var b = new Var("b",1);
		Var c = new Var("c",1);
		Var d = new Var("d",1);
		Var e = new Var("e",1);
		
		Var x = new Var("x",1);
		
		ArrayList<Var> inputs = new ArrayList<Var>(Arrays.asList(a,b,c,d,e));
		ArrayList<Var> outputs = new ArrayList<Var>(List.of(x));
		
		model = new AnalyzerModel();
		model.setVariables(inputs, outputs);
	}

	/*
		a.1 => a
		a+0 => a
	*/
	@Test
	public void testIdentityLaw() {
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("1 a", model);
			expr2 = Parser.parse("0+a", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 1);
			assertEquals(simplifications2.size(), 1);

			SimplificationStep step1 = simplifications1.get(0);
			SimplificationStep step2 = simplifications2.get(0);

			assertEquals("a", step1.newExpression().toString());
			assertEquals(Law.IDENTITY, step1.law());

			assertEquals("a", step2.newExpression().toString());
			assertEquals(Law.IDENTITY, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		0.a => 0
		1+a => 1
	 */
	@Test
	public void testNullLaw() {
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("0 a", model);
			expr2 = Parser.parse("1+a", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 1);
			assertEquals(simplifications2.size(), 1);

			SimplificationStep step1 = simplifications1.get(0);
			SimplificationStep step2 = simplifications2.get(0);

			assertEquals("0", step1.newExpression().toString());
			assertEquals(Law.NULL, step1.law());

			assertEquals("1",step2.newExpression().toString());
			assertEquals(Law.NULL, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		a.a => a
		a+a => a
	 */
	@Test
	public void testIdempotentLaw(){
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("a a", model);
			expr2 = Parser.parse("a+a", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 1);
			assertEquals(simplifications2.size(), 1);

			SimplificationStep step1 = simplifications1.get(0);
			SimplificationStep step2 = simplifications2.get(0);

			assertEquals("a", step1.newExpression().toString());
			assertEquals(Law.IDEMPOTENT, step1.law());

			assertEquals("a",step2.newExpression().toString());
			assertEquals(Law.IDEMPOTENT, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		a.b.a => a.b
		a+b+a => a+b
	 */
	@Test
	public void testIdempotentLawWithSeparateTerms() {
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("a b a", model);
			expr2 = Parser.parse("a+b+a", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(2,simplifications1.size());
			assertEquals(2,simplifications2.size());

			SimplificationStep step1 = simplifications1.get(0);
			SimplificationStep step2 = simplifications2.get(0);

			assertEquals("a⋅b", step1.newExpression().toString());
			assertEquals(Law.IDEMPOTENT, step1.law());

			assertEquals("a+b",step2.newExpression().toString());
			assertEquals(Law.IDEMPOTENT, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}


	/*
		a.~a => 0
		a+~a => 1
	 */
	@Test
	public void testInverseLaw(){
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("a ~a", model);
			expr2 = Parser.parse("a+~a", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 1);
			assertEquals(simplifications2.size(), 1);

			SimplificationStep step1 = simplifications1.get(0);
			SimplificationStep step2 = simplifications2.get(0);

			assertEquals("0", step1.newExpression().toString());
			assertEquals(Law.INVERSE, step1.law());

			assertEquals("1",step2.newExpression().toString());
			assertEquals(Law.INVERSE, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		a+(b.c) => (a+b).(a+c)
		a.(b+c) => (a.b)+(a.c)
	 */
	@Test
	public void testDistributiveLaw(){
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("a+(b c)", model);
			expr2 = Parser.parse("a (b+c)", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 1);
			assertEquals(simplifications2.size(), 1);

			SimplificationStep step1 = simplifications1.get(0);
			SimplificationStep step2 = simplifications2.get(0);

			assertEquals("(a+b)⋅(a+c)", step1.newExpression().toString());
			assertEquals(Law.DISTRIBUTIVE, step1.law());

			assertEquals("a⋅b+a⋅c",step2.newExpression().toString());
			assertEquals(Law.DISTRIBUTIVE, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		(a+b).(a+c) => a+b.c
		(a.b)+(a.c) => a.b+c
	 */
	@Test
	public void testInverseDistributiveLaw(){
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("(a+b) (a+c)", model);
			expr2 = Parser.parse("(a b)+(a c)", model);

			SimplificationStep step1 = AlgebraSimplifier.simplifyExpression(expr1, model, Law.INVERSE_DISTRIBUTIVE);
			SimplificationStep step2 = AlgebraSimplifier.simplifyExpression(expr2, model, Law.INVERSE_DISTRIBUTIVE);

			assertEquals("a+b⋅c", step1.newExpression().toString());
			assertEquals(Law.INVERSE_DISTRIBUTIVE, step1.law());

			assertEquals("a⋅(b+c)",step2.newExpression().toString());
			assertEquals(Law.INVERSE_DISTRIBUTIVE, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
            (a+b).d.(a+c) => (a+b.c).d
            (ab)+d+(ac) => a.(b+c)+d
	*/
	@Test
	public void testInverseDistributiveLawWithSeparateTerms() {
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("(a+b) d (a+c)", model);
			expr2 = Parser.parse("(a b)+d+(a c)", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(2,simplifications1.size());
			assertEquals(2,simplifications2.size());

			SimplificationStep step1 = simplifications1.get(1);
			SimplificationStep step2 = simplifications2.get(1);

			assertEquals("d⋅(a+b⋅c)", step1.newExpression().toString());
			assertEquals(Law.INVERSE_DISTRIBUTIVE, step1.law());

			assertEquals("d+a⋅(b+c)",step2.newExpression().toString());
			assertEquals(Law.INVERSE_DISTRIBUTIVE, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		~a+~b => ~(a.b)
		~a.~b => ~(a+b)
	 */
	@Test
	public void testInverseDeMorgansLaw(){
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("~a+~b", model);
			expr2 = Parser.parse("~a ~b", model);

			SimplificationStep step1 = AlgebraSimplifier.simplifyExpression(expr1, model, Law.INVERSE_DEMORGANS);
			SimplificationStep step2 = AlgebraSimplifier.simplifyExpression(expr2, model, Law.INVERSE_DEMORGANS);

			assertEquals("~(b⋅a)", step1.newExpression().toString());
			assertEquals(Law.INVERSE_DEMORGANS, step1.law());

			assertEquals("~(b+a)",step2.newExpression().toString());
			assertEquals(Law.INVERSE_DEMORGANS, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		a⊕b => (~a.b)+(a.~b)
	 */
	@Test
	public void testInverseXorSimplification(){
		Expression expr1;
		try {
			expr1 = Parser.parse("a⊕b", model);

			SimplificationStep step1 = AlgebraSimplifier.simplifyExpression(expr1, model, Law.INVERSE_XOR);

			assertEquals("~b⋅a+b⋅~a", step1.newExpression().toString());
			assertEquals(Law.INVERSE_XOR, step1.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		a.d.(a+b) => a.d
		a+d+(a.b) => a+d
	 */
	@Test
	public void testAbsorptionLawWithSeparateTerms(){
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("a d (a+b)", model);
			expr2 = Parser.parse("a+d+a b", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 3);
			assertEquals(simplifications2.size(), 3);

			SimplificationStep step1 = simplifications1.get(2);
			SimplificationStep step2 = simplifications2.get(2);

			assertEquals("d⋅a", step1.newExpression().toString());
			assertEquals(Law.ABSORPTION, step1.law());

			assertEquals("d+a",step2.newExpression().toString());
			assertEquals(Law.ABSORPTION, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		a.(a+b) => a
		a+(a.b) => a
	 */
	@Test
	public void testAbsorptionLaw(){
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("a (a+b)", model);
			expr2 = Parser.parse("a+a b", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 2);
			assertEquals(simplifications2.size(), 2);

			SimplificationStep step1 = simplifications1.get(1);
			SimplificationStep step2 = simplifications2.get(1);

			assertEquals("a", step1.newExpression().toString());
			assertEquals(Law.ABSORPTION, step1.law());

			assertEquals("a",step2.newExpression().toString());
			assertEquals(Law.ABSORPTION, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		~(a.b) => ~a + ~b
		~(a+b) => ~a . ~b
	 */
	@Test
	public void testDeMorgansLaw() {
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("~(a b)", model);
			expr2 = Parser.parse("~(a+b)", model);

			List<SimplificationStep> simplifications1 = AlgebraSimplifier.possibleSimplifications(expr1, model);
			List<SimplificationStep> simplifications2 = AlgebraSimplifier.possibleSimplifications(expr2, model);

			assertEquals(simplifications1.size(), 1);
			assertEquals(simplifications2.size(), 1);

			SimplificationStep step1 = simplifications1.get(0);
			SimplificationStep step2 = simplifications2.get(0);

			assertEquals("~a+~b", step1.newExpression().toString());
			assertEquals(Law.DEMORGANS, step1.law());

			assertEquals("~a⋅~b",step2.newExpression().toString());
			assertEquals(Law.DEMORGANS, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}

	/*
		(~a.b)+(a.~b) = a⊕b
		(a+b).(~a+~b) = a⊕b
	 */
	@Test
	public void testXorSimplification() {
		Expression expr1;
		Expression expr2;

		try {
			expr1 = Parser.parse("(~a b)+(a ~b)", model);
			expr2 = Parser.parse("(a+b) (~a+~b)", model);

			SimplificationStep step1 = AlgebraSimplifier.simplifyExpression(expr1, model, Law.XOR);
			SimplificationStep step2 = AlgebraSimplifier.simplifyExpression(expr2, model, Law.XOR);

			assertEquals("a⊕b", step1.newExpression().toString());
			assertEquals(Law.XOR, step1.law());

			assertEquals("a⊕b",step2.newExpression().toString());
			assertEquals(Law.XOR, step2.law());

		} catch (ParserException e1) {
			fail();
		}
	}
}
