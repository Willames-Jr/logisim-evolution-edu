/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static com.cburch.logisim.analyze.Strings.S;

import com.cburch.logisim.analyze.gui.ExpressionView.NamedExpression;
import com.cburch.logisim.analyze.gui.MinimizedTab.NotationModel;
import com.cburch.logisim.analyze.model.*;
import com.cburch.logisim.analyze.model.Expression.Notation;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.PrintHandler;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.StringGetter;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

class AlgebraTab extends AnalyzerTab {
	private static final long serialVersionUID = 1L;
	private final AnalyzerModel model;
	private StringGetter errorMessage;

	private final JTable table = new JTable(1, 1);
	private final JLabel error = new JLabel();

	private class MyListener extends AbstractAction
			implements DocumentListener, OutputExpressionsListener, ItemListener {
		boolean edited = false;

		@Override
		public void itemStateChanged(ItemEvent event) {
			if (event.getSource() == notationChoice) {
				final var not = Notation.values()[notationChoice.getSelectedIndex()];
				if (not != notation) {
					notation = not;
					getField().setText(
							(auxModel == null ? model : auxModel)
							.getOutputExpressions().getExpression(getCurrentVariable()).toString(notation)
							);
					setError(null);
				}
			}
			if (event.getSource() == selector.getComboBox()) {
				clearTextFields();
				insertTextField();
				getField().setText(model.getOutputExpressions().getExpression(getCurrentVariable()).toString(notation));
			}
			
			updateTab();

		}

		@Override
		public void actionPerformed(ActionEvent e) {
			final var output = getCurrentVariable();
			final var notation = Notation.values()[notationChoice.getSelectedIndex()];
			if (e.getSource() == simplificationBtn) {
				//Bloco de testes

				Expression actualExpression = null;
				try {
					actualExpression = Parser.parse(getField().getText(), model);
				} catch (ParserException e1) {
					e1.printStackTrace();
				}

				List<AlgebraSimplifier.SimplificationStep> steps = AlgebraSimplifier.possibleSimplifications(actualExpression, model);
				showSimplifications(steps);

			}
			
			if (e.getSource() == enter) {
				isChecking = true;
				insertUpdate(null);
				isChecking = false;
			}
		}

		@Override
		public void expressionChanged(OutputExpressionsEvent event) {
			final var out = getCurrentVariable();
			
			if (event.getType() == OutputExpressionsEvent.OUTPUT_EXPRESSION) {
				String output = event.getVariable();
				
				if (output.equals(getCurrentVariable())) {
					prettyView.setExpression(model.getOutputExpressions().getExpression(output));
					if (fieldCount == 0) {
						getField().setText(
								model.getOutputExpressions().getExpression(getCurrentVariable()).toString(notation));
						infoLabel.setText(
								"<html><body>Digite a nova expressão no espaço abaixo: <br>Expressão original => "
								+ model.getOutputExpressions().getExpression(getCurrentVariable()).toString(notation) 
								+ "<body></html>");

						getField().setEnabled(false);
						insertTextField();


						//enter.doClick();
					}
					currentStringChanged();
				}
			}
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			
			if (!isChecking) {
				return;
			}

			DefaultListModel<String> listModel = (DefaultListModel<String>) simplificationsList.getModel();
			listModel.removeAllElements();
			
			Expression newExpr;
			if (auxModel == null) {
				auxModel = new AnalyzerModel();
			}
			
			if (fieldCount == 0) {
				getField().setEnabled(false);
				insertTextField();
				return;
			}
			
			//START:setup
			
			final var output = getCurrentVariable();
			final var exprString = getField().getText();
			AnalyzerModel copyModel = new AnalyzerModel();
			copyModel.setCurrentCircuit(model.getCurrentProject(), model.getCurrentCircuit());
			
			ArrayList<Var> outs = new ArrayList<>();
			for (Var ot : model.getOutputs().vars) {
				
				if(ot.name.equals(getCurrentVariable())) {
					outs.add(ot);
					copyModel.setVariables(model.getInputs().vars, outs);
					copyModel.getOutputExpressions().setExpression(ot.name, model.getOutputExpressions().getExpression(ot.name));
				}
			}
			auxModel.setCurrentCircuit(model.getCurrentProject(), model.getCurrentCircuit());
			auxModel.setVariables(model.getInputs().vars, outs);
			// END:setup
			
			
			try {
				newExpr = Parser.parse(exprString, model);
				auxModel.getOutputExpressions().setExpression(output, newExpr);
			} catch (ParserException ex) {
				removeAllTables();
				setError(ex.getMessageGetter());
				
				return;
			}
			
			if (!copyModel.getTruthTable().equals(auxModel.getTruthTable())) {
				getLabel().setText("Incorreto");
				removeAllTables();
				addTruthTable(copyModel.getTruthTable(), "Expressão original:");
				addTruthTable(auxModel.getTruthTable(), "Sua expressão:");
            } else {
				
				if (fieldCount == 0) return;
				
				model.getOutputExpressions().setExpression(output, newExpr, exprString);
	
				String minimalString = auxModel.getOutputExpressions().getMinimalExpression(output).toString(notation);
				String message;
				
				if (newExpr.toString(notation).length() == minimalString.length()) {
					message = "Simplificação mínima";
				} else {
					message = "Simplificação válida, mas não é a mínima";
				}
				getField().setEnabled(false);
				possibleSimplificationsPanel.setVisible(false);
				getLabel().setText(message);
				insertTextField();					
				removeAllTables();

            }
        }

		@Override
		public void removeUpdate(DocumentEvent e) {
			insertUpdate(e);
		}

		@Override
		public void changedUpdate(DocumentEvent e) {
			insertUpdate(e);
		}
		
		private void currentStringChanged() {
			String output = getCurrentVariable();
			String exprString = model.getOutputExpressions().getExpressionString(output);
			curExprStringLength = exprString.length();
			if (!edited) {
				setError(null);
//				updateScreen(true);
//				getField().setText(model.getOutputExpressions().getExpression(output).toString(notation));
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final JComboBox notationChoice = new JComboBox<>(new NotationModel());
	private final JLabel notationLabel = new JLabel();
	private final JLabel infoLabel = new JLabel();
	private boolean isChecking = false;
	private Notation notation = Notation.MATHEMATICAL;
	private final MyListener myListener = new MyListener();
	private final ExpressionView prettyView = new ExpressionView();
	private ArrayList<JPanel> textFields = new ArrayList<>();
	private JPanel fieldsPanel;
	private JButton enter = new JButton();
	private JButton simplificationBtn = new JButton();
	private int curExprStringLength = 0;
	private int fieldCount = 0;
	private JPanel buttons;
	private final OutputSelector selector;
	private JPanel tables;
	private AnalyzerModel auxModel;
	private GridBagLayout gbt;
	private GridBagConstraints gbc;
	private DefaultListModel<String> listModel;
	private JScrollPane inputScrollPane;
	private JList<String> simplificationsList;
	private JPanel possibleSimplificationsPanel;
	public AlgebraTab(AnalyzerModel model, LogisimMenuBar menubar) {
		this.model = model;
		listModel = new DefaultListModel<>();

		model.getOutputExpressions().addOutputExpressionsListener(myListener);
		enter.setToolTipText("Confirmar escolha");
		enter.addActionListener(myListener);

		simplificationBtn.addActionListener(myListener);
		simplificationBtn.setToolTipText("Mostrar possíveis simplificações");
		simplificationBtn.setText("Ajuda");

		selector = new OutputSelector(model);
		selector.addItemListener(myListener);

		gbt = new GridBagLayout();
		gbc = new GridBagConstraints();
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		tables = new JPanel();
		tables.setPreferredSize(new Dimension(200, 400));
		tables.setBorder(BorderFactory.createEmptyBorder());
		tables.setLayout(gbt);

		final var gb = new GridBagLayout();
		final var gc = new GridBagConstraints();
		setLayout(gb);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		gc.weightx = 1.0;
		gc.gridx = 0;
		gc.gridy = GridBagConstraints.RELATIVE;
		gc.weighty = 0.0;
		gc.fill = GridBagConstraints.BOTH;
		final var control = control();
		gb.setConstraints(control, gc);
		add(control);

		gc.fill = GridBagConstraints.HORIZONTAL;
		gb.setConstraints(infoLabel, gc);
		add(infoLabel);

		gc.weighty = 0.0;
		gc.fill = GridBagConstraints.HORIZONTAL;

		simplificationsList = new JList<>(listModel);

		simplificationsList.addListSelectionListener(e -> {
			String selectedExpression = simplificationsList.getSelectedValue().split(":")[1].trim();

			getField().setText(selectedExpression);

			//JOptionPane.showMessageDialog(null, "Expression: " + simplificationsList.getSelectedValue());
		});

		possibleSimplificationsPanel = new JPanel();
		possibleSimplificationsPanel.setLayout(new BoxLayout(possibleSimplificationsPanel, BoxLayout.Y_AXIS));

		possibleSimplificationsPanel.add(new JLabel("Possíveis simplificações: "));

		possibleSimplificationsPanel.add(simplificationsList);
		possibleSimplificationsPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		possibleSimplificationsPanel.setVisible(false);
		//possibleSimplificationsPanel.setBorder(BorderFactory.createEmptyBorder());

		fieldsPanel = new JPanel();
		fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));

		gb.setConstraints(fieldsPanel, gc);


		inputScrollPane = new JScrollPane(fieldsPanel);
		inputScrollPane.setBorder(BorderFactory.createEmptyBorder());

		JPanel mainPanel = new JPanel(new GridLayout(1, 2));

		mainPanel.setBorder(BorderFactory.createEmptyBorder());

		mainPanel.add(inputScrollPane);

		mainPanel.add(tables);
		gb.setConstraints(mainPanel, gc);
		add(mainPanel);

		TableTab newTable = new TableTab(model.getTruthTable());
		newTable.setPreferredSize(new Dimension(AppPreferences.getScaled(60), AppPreferences.getScaled(90)));

	    gc.weighty = 0.0;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gb.setConstraints(error, gc);
		add(error);

		gc.weighty = 1.0;
	    gc.fill = GridBagConstraints.BOTH;
		gb.setConstraints(tables, gc);

		//add(tables);
		addTruthTable(model.getTruthTable(),"Expressão original");

		buttons = new JPanel();
		buttons.add(enter);
		gb.setConstraints(buttons, gc);
		add(buttons);

		myListener.insertUpdate(null);
		setError(null);
		insertTextField();

	}

	JTextField getField() {
		return (JTextField) ( (JPanel) textFields.get(fieldCount).getComponent(0)).getComponent(0);
	}

	JLabel getLabel() {
		return (JLabel) ( (JPanel) textFields.get(fieldCount).getComponent(0)).getComponent(1);
	}

	JLabel getError() {
		return error;
	}
	
	private void clearTextFields() {
		fieldsPanel.removeAll();
		textFields.clear();
		fieldCount = -1;
	}

	private void showSimplifications(List<AlgebraSimplifier.SimplificationStep> possibleSimplifications) {
		if (!possibleSimplifications.isEmpty()) possibleSimplificationsPanel.setVisible(true);

		possibleSimplifications.forEach(simplificationStep -> {
			listModel.addElement(simplificationStep.law().toString() + ": " + simplificationStep.newExpression().toString(notation) );
		});

		getField().requestFocusInWindow();

		if (listModel.isEmpty()) {
			listModel.addElement("Não existe nenhuma simplificação nesse caso");
		};
		possibleSimplificationsPanel.setVisible(true);
	}

	private void insertTextField() {


		GridBagConstraints gbc = new GridBagConstraints();
		JPanel combinedPanel = new JPanel(new GridBagLayout());

		JTextField newField = new JTextField(20);
		JLabel newLabel = new JLabel("");


		JPanel newPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		newPanel.add(newField);
		newPanel.add(newLabel);
		newPanel.add(simplificationBtn);
		newPanel.add(enter);

		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0; // No extra horizontal space
		gbc.weighty = 0.0; // No extra vertical space
		gbc.fill = GridBagConstraints.BOTH; // Don't fill any extra space
		gbc.anchor = GridBagConstraints.NORTH;
		newPanel.setPreferredSize(new Dimension(100,50));
		combinedPanel.add(newPanel, gbc);

		JScrollPane scrollPane = new JScrollPane(possibleSimplificationsPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		bottomPanel.setBorder(BorderFactory.createEmptyBorder());
		bottomPanel.add(scrollPane);

		gbc.gridy = 1;
		gbc.weightx= 0.0;
		combinedPanel.add(bottomPanel, gbc);


		textFields.add(combinedPanel);
		fieldCount++;
		fieldsPanel.add(combinedPanel);
		newPanel.requestFocusInWindow();

		fieldsPanel.revalidate();
		fieldsPanel.repaint();

		updateTab();
	}


	private JPanel control() {
		final var control = new JPanel();
		final var gb = new GridBagLayout();
		final var gc = new GridBagConstraints();
		control.setLayout(gb);
		gc.weightx = 1.0;
		gc.gridwidth = 1;
		gc.gridy = 0;
		gc.gridx = 0;
		gc.fill = GridBagConstraints.VERTICAL;
		gc.anchor = GridBagConstraints.EAST;
		gc.insets = new Insets(3, 10, 3, 10);
		gb.setConstraints(notationLabel, gc);
		control.add(notationLabel);
		gc.gridy++;
		gb.setConstraints(selector.getLabel(), gc);
		control.add(selector.getLabel());
		gc.gridx = 1;
		gc.gridy = 0;
		gc.anchor = GridBagConstraints.WEST;
		gb.setConstraints(notationChoice, gc);
		control.add(notationChoice);
		notationChoice.addItemListener(myListener);
		gc.gridy++;

		gb.setConstraints(selector.getComboBox(), gc);
		control.add(selector.getComboBox());
		return control;
	}

	@Override
	void localeChanged() {
		if (errorMessage != null) {
			error.setText(errorMessage.toString());
		}
		selector.localeChanged();
		enter.setText(S.get("expressionEnterButton"));
		simplificationBtn.setText(S.get("helpSimplificationButton"));
		infoLabel.setText(S.get("outputExpressionInfo"));

		notationLabel.setText(S.get("ExpressionNotation"));
	}

	private void setError(StringGetter msg) {
		if (msg == null) {
			errorMessage = null;
			error.setText(" ");
		} else {
			errorMessage = msg;
			error.setText(msg.toString());
			error.requestFocusInWindow();
		}
	}

	public void addTruthTable(TruthTable table, String title) {
		final var gbn = new GridBagLayout();
		GridBagConstraints gbcn = new GridBagConstraints();
		gbcn.weightx = 1.0;
		gbcn.gridx = 0;
		//gbcn.gridy = GridBagConstraints.CENTER;
		gbcn.weighty = 0.0;
		gbcn.gridy = GridBagConstraints.RELATIVE;
		gbcn.fill = GridBagConstraints.CENTER;

		JLabel tableTitle = new JLabel(title);

		TableTab newTable = new TableTab(table);

		// Set a minimum size for the table (adjust the values as needed)
		newTable.setPreferredSize(new Dimension(200, 200));

		JPanel panel = new JPanel();
		panel.setLayout(gbn);

		newTable.localeChanged();

		gbn.setConstraints(tableTitle, gbcn);
		panel.add(tableTitle);

		gbcn.weightx = 1.0;
		gbcn.weighty = 1.0;
		gbcn.gridy = GridBagConstraints.RELATIVE;
		gbcn.fill = GridBagConstraints.BOTH;
		gbn.setConstraints(newTable, gbcn);
		panel.add(newTable);

		GridBagConstraints gbcTables = new GridBagConstraints();
		gbcTables.weightx = 1.0;
		gbcTables.weighty = 1.0;
		gbcTables.gridx = 0;
		gbcTables.gridy = GridBagConstraints.RELATIVE;
		gbcTables.fill = GridBagConstraints.BOTH;

		gbt.setConstraints(panel, gbcTables);
		tables.add(panel);
		panel.requestFocusInWindow();

		tables.revalidate();
		tables.repaint();

		updateTab();
	}



	public void removeAllTables() {
		tables.removeAll();
		tables.requestFocusInWindow();
		requestFocusInWindow();
		updateTab();
	}

	@Override
	void updateTab() {
		String output = getCurrentVariable();
		prettyView.setExpression(model.getOutputExpressions().getExpression(output));
		myListener.currentStringChanged();
	}

	String getCurrentVariable() {
		return selector.getSelectedOutput();
	}

	@Override
	EditHandler getEditHandler() {
		return editHandler;
	}

	final EditHandler editHandler = new EditHandler() {
		@Override
		public void computeEnabled() {
			final var viewing = table.getSelectedRow() >= 0;
			final var editing = table.isEditing();
			setEnabled(LogisimMenuBar.CUT, editing);
			setEnabled(LogisimMenuBar.COPY, viewing);
			setEnabled(LogisimMenuBar.PASTE, viewing);
			setEnabled(LogisimMenuBar.DELETE, editing);
			setEnabled(LogisimMenuBar.DUPLICATE, false);
			setEnabled(LogisimMenuBar.SELECT_ALL, editing);
			setEnabled(LogisimMenuBar.RAISE, false);
			setEnabled(LogisimMenuBar.LOWER, false);
			setEnabled(LogisimMenuBar.RAISE_TOP, false);
			setEnabled(LogisimMenuBar.LOWER_BOTTOM, false);
			setEnabled(LogisimMenuBar.ADD_CONTROL, false);
			setEnabled(LogisimMenuBar.REMOVE_CONTROL, false);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			final var action = e.getSource();
			if (table.getSelectedRow() < 0)
				return;
			table.getActionMap().get(action).actionPerformed(e);
		}
	};

	private class ExpressionTransferHandler extends TransferHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean importData(TransferHandler.TransferSupport info) {
			String s;
			try {
				s = (String) info.getTransferable().getTransferData(DataFlavor.stringFlavor);
			} catch (Exception e) {
				setError(S.getter("cantImportFormatError"));
				return false;
			}

			Expression expr;
			try {
				expr = Parser.parseMaybeAssignment(s, model);
				setError(null);
			} catch (ParserException ex) {
				setError(ex.getMessageGetter());
				return false;
			}
			if (expr == null)
				return false;

			var idx = -1;
			if (table.getRowCount() == 0) {
				return false;
			}
			if (table.getRowCount() == 1) {
				idx = 0;
			} else if (info.isDrop()) {
				try {
					final var dl = (JTable.DropLocation) info.getDropLocation();
					idx = dl.getRow();
				} catch (ClassCastException ignored) {
					// do nothing
				}
			} else {
				idx = table.getSelectedRow();
				if (idx < 0 && Expression.isAssignment(expr)) {
					final var v = Expression.getAssignmentVariable(expr);
					for (idx = table.getRowCount() - 1; idx >= 0; idx--) {
						final var ne = (NamedExpression) table.getValueAt(idx, 0);
						if (v.equals(ne.name))
							break;
					}
				}
			}
			if (idx < 0 || idx >= table.getRowCount())
				return false;
			if (Expression.isAssignment(expr))
				expr = Expression.getAssignmentExpression(expr);

			final var ne = (NamedExpression) table.getValueAt(idx, 0);
			ne.exprString = s;
			ne.expr = expr;
			ne.err = null;
			table.setValueAt(ne, idx, 0);

			return true;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			final var idx = table.getSelectedRow();
			if (idx < 0)
				return null;
			final var ne = (NamedExpression) table.getValueAt(idx, 0);
			final var s = ne.expr != null ? ne.expr.toString(notation) : ne.err;
			return s == null ? null : new StringSelection(ne.name + " = " + s);
		}

		@Override
		public int getSourceActions(JComponent c) {
			return COPY;
		}

		@Override
		protected void exportDone(JComponent c, Transferable tdata, int action) {
			// dummy
		}

		@Override
		public boolean canImport(TransferHandler.TransferSupport support) {
			return table.getRowCount() > 0 && support.isDataFlavorSupported(DataFlavor.stringFlavor);
		}
	}

	@Override
	PrintHandler getPrintHandler() {
		return printHandler;
	}

	final PrintHandler printHandler = new PrintHandler() {
		@Override
		public Dimension getExportImageSize() {
			final var width = table.getWidth();
			var height = 14;
			final var n = table.getRowCount();
			for (var i = 0; i < n; i++) {
				final var ne = (NamedExpression) table.getValueAt(i, 0);
				prettyView.setWidth(width);
				prettyView.setExpression(ne);
				height += prettyView.getExpressionHeight() + 14;
			}
			return new Dimension(width + 6, height);
		}

		@Override
		public void paintExportImage(BufferedImage img, Graphics2D g) {
			final var width = img.getWidth();
			final var height = img.getHeight();
			g.setClip(0, 0, width, height);
			g.translate(6 / 2, 14);
			final var n = table.getRowCount();
			for (var i = 0; i < n; i++) {
				final var ne = (NamedExpression) table.getValueAt(i, 0);
				prettyView.setForeground(Color.BLACK);
				prettyView.setBackground(Color.WHITE);
				prettyView.setWidth(width - 6);
				prettyView.setExpression(ne);
				final var rh = prettyView.getExpressionHeight();
				prettyView.setSize(new Dimension(width - 6, rh));
				prettyView.paintComponent(g);
				g.translate(0, rh + 14);
			}
		}

		@Override
		public int print(Graphics2D g, PageFormat pf, int pageNum, double w, double h) {
			final var width = (int) Math.ceil(w);
			g.translate(6 / 2, 14 / 2);

			final var n = table.getRowCount();
			var y = 0;
			var pg = 0;
			for (var i = 0; i < n; i++) {
				final var ne = (NamedExpression) table.getValueAt(i, 0);
				prettyView.setWidth(width - 6);
				prettyView.setForeground(Color.BLACK);
				prettyView.setBackground(Color.WHITE);
				prettyView.setExpression(ne);
				int rh = prettyView.getExpressionHeight();
				if (y > 0 && y + rh > h) {
					// go to next page
					y = 0;
					pg++;
					if (pg > pageNum)
						return Printable.PAGE_EXISTS; // done the page we wanted
				}
				if (pg == pageNum) {
					prettyView.setSize(new Dimension(width - 6, rh));
					prettyView.paintComponent(g);
					g.translate(0, rh + 14);
				}
				y += rh + 14;
			}
			return (pg < pageNum ? Printable.NO_SUCH_PAGE : Printable.PAGE_EXISTS);
		}
	};
}
