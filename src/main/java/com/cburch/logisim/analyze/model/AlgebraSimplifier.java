package com.cburch.logisim.analyze.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AlgebraSimplifier {
	public enum Law {
		IDENTITY,
		NULL,
		IDEMPOTENT,
		INVERSE,
		COMMUTATIVE,
		ASSOCIATIVE,
		DISTRIBUTIVE,
		INVERSE_DISTRIBUTIVE,
		ABSORPTION,
		DEMORGANS,
		INVERSE_DEMORGANS,
		XOR,
		INVERSE_XOR,
	}

	public record SimplificationStep(Expression newExpression, Expression oldExpression, Law law) {}

	// A ideia é que essa classe represente
	// a expressão como um nó de uma árvore
	// Talvez o "model" poderia ficar fora
	// Mas não vejo onde colocar
	private static class ExpressionNode {
		public int key;
		public Expression value;
		public AnalyzerModel model;

		public ExpressionNode(int key, Expression value, AnalyzerModel model) {
			this.key = key;
			this.value = value;
			this.model = model;
		}

		@Override
		public boolean equals(Object ob) {
			if (!(ob instanceof ExpressionNode expressionNodeObj)) {
				return false;
			}

            return (expressionNodeObj.key == this.key)
                    && (compareExpressions(expressionNodeObj.value, this.value, this.model));
        }

		@Override
		public int hashCode() {
			return Objects.hash(this.key, this.value, this.model);
		}
	}

	public static boolean compareExpressions(Expression a, Expression b, AnalyzerModel model) {
		final String output = model.getOutputs().vars.get(0).toString();
		AnalyzerModel modelA = new AnalyzerModel();
		AnalyzerModel modelB = new AnalyzerModel();

		modelA.setVariables(model.getInputs().vars, model.getOutputs().vars);
		modelB.setVariables(model.getInputs().vars, model.getOutputs().vars);

		modelA.getOutputExpressions().setExpression(output, a);
		modelB.getOutputExpressions().setExpression(output, b);

		return modelA.getTruthTable().equals(modelB.getTruthTable());
	}

	private static boolean simplified = false;

	public static SimplificationStep simplifyExpression(Expression expr, AnalyzerModel model, Law lawToApply) {

		simplified = false;

		Map<ExpressionNode,ArrayList<ExpressionNode>> expressionsMap = new HashMap<>();
		Map<ExpressionNode, ExpressionNode> expressionsToRebuild = new HashMap<>();

		expr.visit(new Expression.Visitor<Expression>() {

			int counter = 0;

			public Expression getFather(Expression a, Expression b, Expression.Op op) {
                return switch (op) {
                    case AND -> Expressions.and(a, b);
                    case OR -> Expressions.or(a, b);
                    case XOR -> Expressions.xor(a, b);
                    case XNOR -> Expressions.xnor(a, b);
                    default -> null;
                };
			}

			private void addExpression(ExpressionNode key, ExpressionNode exp) {
				expressionsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(exp);
			}

			private ArrayList<ExpressionNode> expressionsToCompare(ExpressionNode key) {
				ArrayList<ExpressionNode> expressions = new ArrayList<>();

				if (expressionsMap.containsKey(key)) {
					for (ExpressionNode ex : expressionsMap.get(key)) {
						expressions.add(ex);
						if (ex.value.getOp() == key.value.getOp()) {
							expressions.addAll(expressionsToCompare(ex));
						}
					}
				}

				return expressions;
			}

			private boolean canApplyIdempotent(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare) {
				int matchingKeys = 0;

				for (ExpressionNode ex : tempCompare) {
					if (compareExpressions(ex.value, exp.value, model)) {
						if (ex.key == exp.key) {
							matchingKeys++;
						} else {
							return true;
						}
					}
				}

				return matchingKeys > 1;
			}

			private Map<ExpressionNode, ExpressionNode> canApplyInverse(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode, ExpressionNode> expressionsToChange = new HashMap<>();

				for (ExpressionNode ex : tempCompare) {
					Expression notExp = Expressions.not(exp.value);
					if (compareExpressions(notExp, ex.value, model) || compareExpressions(ex.value, notExp, model)) {
						if (op == Expression.Op.AND || op == Expression.Op.OR) {
							expressionsToChange.put(ex, null);
							expressionsToChange.put(exp, new ExpressionNode(exp.key, Expressions.constant(op == Expression.Op.AND ? 0 : 1), model));
							return expressionsToChange;
						}
					}
				}

				return expressionsToChange;
			}

			private Map<ExpressionNode,ExpressionNode> canApplyNull(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode,ExpressionNode> expressionsToChange = new HashMap<>();

				Expression zeroConstant = Expressions.constant(0);
				Expression oneConstant = Expressions.constant(1);

				for (ExpressionNode ex : tempCompare) {
					// Nesse caso exp é uma constante
					if ((exp.value.equals(zeroConstant) || exp.value.equals(oneConstant))
						&&
						(!ex.value.equals(zeroConstant) && !ex.value.equals(oneConstant))) {
						if (exp.value.hashCode() == 0 && op == Expression.Op.AND) {
							expressionsToChange.put(ex, null);
							break;
						}
						if (exp.value.hashCode() == 1 && op == Expression.Op.OR) {
							expressionsToChange.put(ex, null);
							break;
						}
					}

					// Nesse caso ex é uma constante
					if ((!exp.value.equals(zeroConstant) && !exp.value.equals(oneConstant))
							&&
							(ex.value.equals(zeroConstant) || ex.value.equals(oneConstant))) {
						if (ex.value.hashCode() == 0 && op == Expression.Op.AND) {
							expressionsToChange.put(exp,null);
							break;
						}
						if (ex.value.hashCode() == 1 && op == Expression.Op.OR) {
							expressionsToChange.put(exp,null);
							break;
						}
					}

				}

				return expressionsToChange;
			}

			// Talvez esse método seja "overkill" para o problema
			// Uma simples comparação entre duas expressões já iria servir
			// Não é o mesmo problema que existe na idempotent por exemplo
			// 0+a+b = a+b   a+0+b = a+b  a+b+0 = a+b
			// Fazendo a comparação com pares próximos já bastaria
			private Map<ExpressionNode,ExpressionNode> canApplyIdentity(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode,ExpressionNode> expressionsToChange = new HashMap<>();

				Expression zeroConstant = Expressions.constant(0);
				Expression oneConstant = Expressions.constant(1);

				for (ExpressionNode ex : tempCompare) {
					boolean expIsConstant = exp.value.equals(zeroConstant) || exp.value.equals(oneConstant);
					boolean exIsConstant = ex.value.equals(zeroConstant) || ex.value.equals(oneConstant);

					if (expIsConstant && !exIsConstant && exp.value.hashCode() == (op == Expression.Op.AND ? 1 : 0)) {
						expressionsToChange.put(exp, null);
						break;
					}

					if (!expIsConstant && exIsConstant && ex.value.hashCode() == (op == Expression.Op.AND ? 1 : 0)) {
						expressionsToChange.put(ex, null);
						break;
					}
				}
				return expressionsToChange;
			}

			private Map<ExpressionNode, ExpressionNode> canApplyDistributive(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode, ExpressionNode> expressionsToChange = new HashMap<>();

				Expression.Visitor<List<Expression>> binaryVisitor = new Expression.Visitor<>() {
					@Override
					public List<Expression> visitBinary(Expression a, Expression b, Expression.Op op) {
						return List.of(a, b);
					}
				};

				List<Expression> expChildren = exp.value.visit(binaryVisitor);
				boolean expIsBinary = expChildren != null;

				for (ExpressionNode ex : tempCompare) {
					List<Expression> exChildren = ex.value.visit(binaryVisitor);
					boolean exIsBinary = exChildren != null;
					ExpressionNode expressionA = expIsBinary ? exp : ex;
					ExpressionNode expressionB = expIsBinary ? ex : exp;

					if (ex.equals(exp)) {
						continue;
					}

					if (exIsBinary || expIsBinary){
                        //assert expChildren != null;
                        expressionsToChange.putAll(canApplyDistributiveHelper(expressionA, expChildren, expressionB, op));
					}


				}

				return expressionsToChange;
			}

			private Map<ExpressionNode, ExpressionNode> canApplyDistributiveHelper(ExpressionNode exp, List<Expression> expChildren, ExpressionNode ex, Expression.Op op) {
				Map<ExpressionNode, ExpressionNode> changes = new HashMap<>();

				if (expChildren == null) return changes;

				if (compareExpressions(exp.value, expChildren.get(0), model) ||
						compareExpressions(exp.value, expChildren.get(1), model) ||
						compareExpressions(expChildren.get(1), expChildren.get(0), model)) {
					return changes;
				}

				Expression newExpression;
				if (op == Expression.Op.OR) {
					newExpression = Expressions.and(
							Expressions.or(ex.value, expChildren.get(0)),
							Expressions.or(ex.value, expChildren.get(1))
					);
				} else if (op == Expression.Op.AND) {
					newExpression = Expressions.or(
							Expressions.and(ex.value, expChildren.get(0)),
							Expressions.and(ex.value, expChildren.get(1))
					);
				} else {
					return changes;
				}

				changes.put(exp, null);
				changes.put(ex, new ExpressionNode(exp.key, newExpression, model));
				return changes;
			}

			private Map<ExpressionNode, ExpressionNode> canApplyInverseDistributive(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode, ExpressionNode> expressionsToChange = new HashMap<>();

				Expression.Visitor<List<Expression>> visitor = new Expression.Visitor<>() {
					@Override
					public List<Expression> visitBinary(Expression a, Expression b, Expression.Op op) {
						return List.of(a, b);
					}
				};

				List<Expression> expChildren = exp.value.visit(visitor);
				boolean expIsBinary = expChildren != null;

				if (expIsBinary && compareExpressions(expChildren.get(0), expChildren.get(1), model)) {
					return expressionsToChange;
				}

				for (ExpressionNode ex : tempCompare) {
					List<Expression> exChildren = ex.value.visit(visitor);
					boolean exIsBinary = exChildren != null;

					if (exIsBinary && compareExpressions(exChildren.get(0), exChildren.get(1), model)) {
						continue;
					}

					if (expIsBinary && exIsBinary) {
						Map<ExpressionNode, ExpressionNode> changes = applyInverseDistributiveHelper(exp, expChildren, exChildren, ex, op);
						expressionsToChange.putAll(changes);
						if (!changes.isEmpty()) {
							break;
						}
					}
				}

				return expressionsToChange;
			}

			private Map<ExpressionNode, ExpressionNode> applyInverseDistributiveHelper(ExpressionNode exp, List<Expression> expChildren, List<Expression> exChildren, ExpressionNode ex, Expression.Op op) {
				Map<ExpressionNode, ExpressionNode> changes = new HashMap<>();

				Expression a = null;
				Expression b = null;
				Expression c = null;

				if ((compareExpressions(expChildren.get(0), exChildren.get(0), model)
						|| compareExpressions(expChildren.get(0), exChildren.get(1), model))
						&&
						(!compareExpressions(expChildren.get(1), exChildren.get(0), model)
								&& !compareExpressions(expChildren.get(1), exChildren.get(1), model))) {
					a = expChildren.get(0);
					c = expChildren.get(1);
					b = compareExpressions(a, exChildren.get(0), model) ? exChildren.get(1) : exChildren.get(0);
				}

				if ((compareExpressions(expChildren.get(1), exChildren.get(0), model)
						|| compareExpressions(expChildren.get(1), exChildren.get(1), model))
						&&
						(!compareExpressions(expChildren.get(0), exChildren.get(0), model)
								&& !compareExpressions(expChildren.get(0), exChildren.get(1), model))) {
					a = expChildren.get(1);
					c = expChildren.get(0);
					b = compareExpressions(a, exChildren.get(0), model) ? exChildren.get(1) : exChildren.get(0);
				}

				if (a != null && b != null && c != null) {
					Expression newExp = (op == Expression.Op.OR) ?
							Expressions.and(a, Expressions.or(b, c)) :
							Expressions.or(a, Expressions.and(b, c));

					changes.put(exp, new ExpressionNode(exp.key, newExp, model));
					changes.put(ex, null);
				}

				return changes;
			}


			private Map<ExpressionNode, ExpressionNode> canApplyAbsorption(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op ignoredOp) {
				Map<ExpressionNode, ExpressionNode> expressionsToChange = new HashMap<>();

				Expression.Visitor<List<Expression>> visitor = new Expression.Visitor<>() {
					@Override
					public List<Expression> visitBinary(Expression a, Expression b, Expression.Op op) {
						return List.of(a, b);
					}
				};

				List<Expression> expChildren = exp.value.visit(visitor);
				boolean expIsBinary = expChildren != null;

				for (ExpressionNode ex : tempCompare) {
					if (ex.equals(exp)) {
						continue;  // Skip comparison with itself
					}

					List<Expression> exChildren = ex.value.visit(visitor);
					boolean exIsBinary = exChildren != null;

					if (expIsBinary && (ex.value.equals(expChildren.get(0)) || ex.value.equals(expChildren.get(1)))) {
						Expression newExp = ex.value;
						expressionsToChange.put(ex, null);
						expressionsToChange.put(exp, new ExpressionNode(exp.key, newExp, model));
						break;
					}

					if (exIsBinary && (exp.value.equals(exChildren.get(0)) || exp.value.equals(exChildren.get(1)))) {
						Expression newExp = exp.value;
						expressionsToChange.put(ex, null);
						expressionsToChange.put(exp, new ExpressionNode(exp.key, newExp, model));
						break;
					}
				}

				return expressionsToChange;
			}

			private Map<ExpressionNode, ExpressionNode> canApplyDeMorgans(ExpressionNode exp) {
				Map<ExpressionNode, ExpressionNode> expressionsToChange = new HashMap<>();

				List<Expression> expChildren = Objects.requireNonNullElseGet(
						exp.value.visit(new Expression.Visitor<List<Expression>>() {
							@Override
							public List<Expression> visitBinary(Expression a, Expression b, Expression.Op op) {
								return List.of(a, b);
							}
						}),
						ArrayList::new  // Default to an empty list if expChildren is null
				);

				if (expChildren.size() != 2) {
					return expressionsToChange;
				}

				Expression newExp = (exp.value.getOp() == Expression.Op.AND) ?
						Expressions.or(Expressions.not(expChildren.get(0)), Expressions.not(expChildren.get(1))) :
						Expressions.and(Expressions.not(expChildren.get(0)), Expressions.not(expChildren.get(1)));

				expressionsToChange.put(exp, new ExpressionNode(exp.key, newExp, model));

				return expressionsToChange;
			}

			private Map<ExpressionNode, ExpressionNode> canApplyInverseDeMorgans(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode, ExpressionNode> expressionsToChange = new HashMap<>();

				if (exp.value.getOp() != Expression.Op.NOT) {
					return expressionsToChange;
				}

				for (ExpressionNode ex : tempCompare) {
					if (ex.value.getOp() != Expression.Op.NOT || ex.equals(exp)) {
						continue;
					}

					Expression.Visitor<Expression> visitor = new Expression.Visitor<>() {
						@Override
						public Expression visitNot(Expression a) {
							return a;
						}
					};

					Expression operandA = ex.value.visit(visitor);
					Expression operandB = exp.value.visit(visitor);

					Expression newExp = Expressions.not(
							op == Expression.Op.AND ? Expressions.or(operandB, operandA) : Expressions.and(operandB, operandA)
					);

					expressionsToChange.put(ex, null);
					expressionsToChange.put(exp, new ExpressionNode(exp.key, newExp, model));
					break;
				}

				return expressionsToChange;
			}


			private Map<ExpressionNode,ExpressionNode> canApplyXor(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode,ExpressionNode> expressionsToChange = new HashMap<>();

				Expression.Visitor<List<Expression>> visitor = new Expression.Visitor<>() {
					@Override
					public List<Expression> visitBinary(Expression a, Expression b, Expression.Op op) {
						return List.of(a,b);
					}
				};

				List<Expression> expChildren = exp.value.visit(visitor);
				boolean expIsBinary = expChildren != null;


				for (ExpressionNode ex: tempCompare) {
					List<Expression> exChildren = ex.value.visit(visitor);
					boolean exIsBinary = exChildren != null;

					if (!exIsBinary || !expIsBinary) {
						continue;
					}

					if (compareExpressions(exChildren.get(0),exChildren.get(1),model)) {
						continue;
					}

					if (compareExpressions(expChildren.get(0),expChildren.get(1),model)) {
						continue;
					}
					// Check if the expression is in this format:
					// (~a.b)+(a.~b) = a⊕b
					if (op == Expression.Op.OR) {
						Expression aNot = null;
						// "Normal" it's just an expression without the not
						Expression aNormal = null;
						Expression bNot = null;
						Expression bNormal = null;

						if ( (exChildren.get(0).getOp() == Expression.Op.NOT && exChildren.get(1).getOp() != Expression.Op.NOT)
								|| (exChildren.get(1).getOp() == Expression.Op.NOT && exChildren.get(0).getOp() != Expression.Op.NOT)) {
							aNot = exChildren.get(0).getOp() == Expression.Op.NOT ? exChildren.get(0) : exChildren.get(1);
							aNormal = exChildren.get(0).getOp() != Expression.Op.NOT ? exChildren.get(0) : exChildren.get(1);
						}

						if ( (expChildren.get(0).getOp() == Expression.Op.NOT && expChildren.get(1).getOp() != Expression.Op.NOT)
								|| (expChildren.get(1).getOp() == Expression.Op.NOT && expChildren.get(0).getOp() != Expression.Op.NOT)) {
							bNot = expChildren.get(0).getOp() == Expression.Op.NOT ? expChildren.get(0) : expChildren.get(1);
							bNormal = expChildren.get(0).getOp() != Expression.Op.NOT ? expChildren.get(0) : expChildren.get(1);
						}

						if (bNot == null || bNormal == null || aNot == null || aNormal == null) {
							continue;
						}

						if (compareExpressions(aNot, Expressions.not(bNormal), model)
								&& compareExpressions(bNot, Expressions.not(aNormal), model)
						) {
							Expression newExp = Expressions.xor(bNormal,aNormal);

							expressionsToChange.put(ex,null);

							expressionsToChange.put(exp,
									new ExpressionNode(exp.key,newExp, model)
							);
							break;
						}
					}

					// Check if the expression is in this format:
					// (a+b) . (~a+~b) = a⊕b
					if (op == Expression.Op.AND) {
						if (compareExpressions(exChildren.get(0),exChildren.get(1),model)) {
							continue;
						}

						Expression a = exChildren.get(0);
						Expression b = exChildren.get(1);
						Expression c = expChildren.get(0);
						Expression d = expChildren.get(1);

						if ((a.getOp() == Expression.Op.NOT && b.getOp() == Expression.Op.NOT)
								&&
								(c.getOp() != Expression.Op.NOT && d.getOp() != Expression.Op.NOT)){

							if ((compareExpressions(Expressions.not(c), a, model)
									&&
									compareExpressions(Expressions.not(d), b, model))
									||
									(compareExpressions(Expressions.not(d), a, model)
											&&
											compareExpressions(Expressions.not(c), b, model))) {
								Expression newExp = Expressions.xor(c, d);

								expressionsToChange.put(ex,null);

								expressionsToChange.put(exp,
										new ExpressionNode(exp.key,newExp, model)
								);
								break;
							}

						}

						if ((a.getOp() != Expression.Op.NOT && b.getOp() != Expression.Op.NOT)
								&&
								(c.getOp() == Expression.Op.NOT && d.getOp() == Expression.Op.NOT)){

							if ((compareExpressions(Expressions.not(a), c, model)
									&&
									compareExpressions(Expressions.not(b), d, model))
									||
									(compareExpressions(Expressions.not(a), d, model)
											&&
											compareExpressions(Expressions.not(b), c, model))) {
								Expression newExp = Expressions.xor(a, b);

								expressionsToChange.put(ex,null);

								expressionsToChange.put(exp,
										new ExpressionNode(exp.key,newExp, model)
								);
								break;
							}

						}
					}

				}

				return expressionsToChange;
			}


			private Map<ExpressionNode,ExpressionNode> canApplyInverseXor(ExpressionNode exp, ArrayList<ExpressionNode> tempCompare, Expression.Op op) {
				Map<ExpressionNode,ExpressionNode> expressionsToChange = new HashMap<>();

				if (op != Expression.Op.XOR) {
					return expressionsToChange;
				}

				for (ExpressionNode ex: tempCompare) {
					if (ex.equals(exp)) {
						continue;
					}

					Expression newExp = Expressions.or(
							 Expressions.and(Expressions.not(exp.value), ex.value),
							 Expressions.and(exp.value, Expressions.not(ex.value))
							);

					expressionsToChange.put(ex,null);

					expressionsToChange.put(exp,
							new ExpressionNode(exp.key,newExp, model)
							);
					break;
				}

				return expressionsToChange;
			}

			@Override
			public Expression visitBinary(Expression a, Expression b, Expression.Op op) {
				counter += 1;
				int actualCounter = counter;
	            ExpressionNode father = new ExpressionNode(actualCounter,getFather(a, b, op), model);
	            ExpressionNode actualA = new ExpressionNode(counter+1,a, model);
	            ExpressionNode actualB = new ExpressionNode(counter+1,b, model);
	            Expression r;
	            Expression l;

	            ArrayList<ExpressionNode> tempExpressions;

	            addExpression(father, actualA);

	            tempExpressions = expressionsToCompare(father);

	            if (lawToApply == Law.IDEMPOTENT && canApplyIdempotent(actualA, tempExpressions) && !simplified) {
	            	expressionsToRebuild.put(actualA, new ExpressionNode(actualA.key,null, model));
	            } else if (lawToApply == Law.IDENTITY && !canApplyIdentity(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyIdentity(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.NULL && !canApplyNull(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyNull(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE && !canApplyInverse(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverse(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.XOR && !canApplyXor(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyXor(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE_XOR && !canApplyInverseXor(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverseXor(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.DISTRIBUTIVE && !canApplyDistributive(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyDistributive(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE_DISTRIBUTIVE && !canApplyInverseDistributive(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverseDistributive(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.ABSORPTION && !canApplyAbsorption(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyAbsorption(actualA, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE_DEMORGANS && !canApplyInverseDeMorgans(actualA, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverseDeMorgans(actualA, tempExpressions, op));
	            }

	            if (!expressionsToRebuild.isEmpty()) {
	            	simplified = true;
	            }

				l = a.visit(this);

	            addExpression(father, actualB);
	            tempExpressions = expressionsToCompare(father);

	            if (lawToApply == Law.IDEMPOTENT && canApplyIdempotent(actualB, tempExpressions) && !simplified) {
	            	expressionsToRebuild.put(actualB, new ExpressionNode(actualB.key,null, model));
	            } else if (lawToApply == Law.IDENTITY && !canApplyIdentity(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyIdentity(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.NULL && !canApplyNull(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyNull(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE && !canApplyInverse(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverse(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.XOR && !canApplyXor(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyXor(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE_XOR && !canApplyInverseXor(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverseXor(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.DISTRIBUTIVE && !canApplyDistributive(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyDistributive(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE_DISTRIBUTIVE && !canApplyInverseDistributive(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverseDistributive(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.ABSORPTION && !canApplyAbsorption(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyAbsorption(actualB, tempExpressions, op));
	            } else if (lawToApply == Law.INVERSE_DEMORGANS && !canApplyInverseDeMorgans(actualB, tempExpressions, op).isEmpty() && !simplified) {
	            	expressionsToRebuild.putAll(canApplyInverseDeMorgans(actualB, tempExpressions, op));
	            }

	            if (!expressionsToRebuild.isEmpty()) {
	            	simplified = true;
	            }

	        	r = b.visit(this);

                if (op == Expression.Op.OR) {
                	return Expressions.or(l, r);
                } else {
                	return Expressions.and(l,r);
                }
			}

			@Override
			public Expression visitVariable(String name) {
				return Expressions.variable(name);
			}

			@Override
			public Expression visitNot(Expression a) {

				counter += 1;
				ExpressionNode actualA = new ExpressionNode(counter+1,a, model);

				if (lawToApply == Law.DEMORGANS && !canApplyDeMorgans(actualA).isEmpty() && !simplified) {
					expressionsToRebuild.putAll(canApplyDeMorgans(actualA));
					simplified = true;
				}

				return Expressions.not(a);
			}

			@Override
			public Expression visitConstant(int value) {
				return Expressions.constant(value);
			}
		});

		if (!simplified) return null;


		return new SimplificationStep(rebuildExpression(expr, model, expressionsToRebuild), expr, lawToApply);

	}

	private static Expression rebuildExpression(Expression expr, AnalyzerModel model, Map<ExpressionNode,ExpressionNode> expressionsToChange) {

		return expr.visit(new Expression.Visitor<>() {

			int counter = 0;
			@Override
			public Expression visitBinary(Expression a, Expression b, Expression.Op op) {
				counter += 1;
				ExpressionNode actualA = new ExpressionNode(counter+1,a, model);
	            ExpressionNode actualB = new ExpressionNode(counter+1,b, model);
	            Expression r;
	            Expression l;

	            if (expressionsToChange.containsKey(actualA)) {
	            	l = expressionsToChange.get(actualA) == null ? null : expressionsToChange.get(actualA).value;
	            	expressionsToChange.remove(actualA);
	            } else {
	            	l = a.visit(this);
	            }

	            if (expressionsToChange.containsKey(actualB)) {
	            	r = expressionsToChange.get(actualB) == null ? null : expressionsToChange.get(actualB).value;

	            	expressionsToChange.remove(actualB);
	            } else {
	            	r = b.visit(this);
	            }

                if (op == Expression.Op.OR) {
                	return Expressions.or(l, r);
                } else {
                	return Expressions.and(l,r);
                }
			}

			@Override
			public Expression visitConstant(int value) {
				return Expressions.constant(value);
			}

			@Override
			public Expression visitNot(Expression a) {
				counter += 1;
				ExpressionNode actualA = new ExpressionNode(counter+1,a, model);
				ExpressionNode defaultReturn = new ExpressionNode(0, Expressions.not(a), model);

				return expressionsToChange.getOrDefault(actualA, defaultReturn).value;
			}

			@Override
			public Expression visitVariable(String name) {

				return Expressions.variable(name);
			}


		});
	}

	public static List<SimplificationStep> possibleSimplifications(Expression expr, AnalyzerModel model) {
		ArrayList<SimplificationStep> simplifications = new ArrayList<>();

		for (Law law : Law.values()) {
			SimplificationStep step = simplifyExpression(expr, model, law);
			if (step != null) {
				simplifications.add(step);
			}
		}

		return simplifications;
	}
}
