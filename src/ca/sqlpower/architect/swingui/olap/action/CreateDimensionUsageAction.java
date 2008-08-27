/*
 * Copyright (c) 2008, SQL Power Group Inc.
 *
 * This file is part of Power*Architect.
 *
 * Power*Architect is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Power*Architect is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.architect.swingui.olap.action;

import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;

import ca.sqlpower.architect.ArchitectException;
import ca.sqlpower.architect.ArchitectRuntimeException;
import ca.sqlpower.architect.SQLColumn;
import ca.sqlpower.architect.SQLRelationship;
import ca.sqlpower.architect.SQLTable;
import ca.sqlpower.architect.SQLRelationship.ColumnMapping;
import ca.sqlpower.architect.olap.OLAPUtil;
import ca.sqlpower.architect.olap.MondrianModel.Cube;
import ca.sqlpower.architect.olap.MondrianModel.Dimension;
import ca.sqlpower.architect.olap.MondrianModel.DimensionUsage;
import ca.sqlpower.architect.olap.MondrianModel.Hierarchy;
import ca.sqlpower.architect.olap.MondrianModel.Table;
import ca.sqlpower.architect.swingui.ArchitectSwingSession;
import ca.sqlpower.architect.swingui.PlayPen;
import ca.sqlpower.architect.swingui.olap.CubePane;
import ca.sqlpower.architect.swingui.olap.DimensionPane;
import ca.sqlpower.architect.swingui.olap.DimensionUsageEditPanel;
import ca.sqlpower.architect.swingui.olap.UsageComponent;
import ca.sqlpower.swingui.DataEntryPanel;
import ca.sqlpower.swingui.DataEntryPanelBuilder;

/**
 * Creates a dimension usage after the user clicks a DimensionPane and a
 * CubePane.
 */
public class CreateDimensionUsageAction extends CreateUsageAction<DimensionPane, CubePane> {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(CreateDimensionUsageAction.class);

    public CreateDimensionUsageAction(ArchitectSwingSession session, PlayPen pp) {
        super(session, pp, DimensionPane.class, CubePane.class, "Dimension Usage");
    }

    @Override
    protected void createUsage(DimensionPane dp, CubePane cp) {
        final Dimension dimension = dp.getModel();
        final Cube cube = cp.getModel();
        if (OLAPUtil.isNameUnique(cp.getModel(), DimensionUsage.class, dimension.getName())) {
            final DimensionUsage du = new DimensionUsage();
            du.setName(dimension.getName());
            du.setSource(dimension.getName());
            cube.addChild(du);
            UsageComponent uc = new UsageComponent(playpen.getContentPane(), du, dp, cp);
            playpen.getContentPane().add(uc, playpen.getContentPane().getComponentCount());
            
            try {
                SQLTable factTable = OLAPUtil.getSQLTableFromOLAPTable(OLAPUtil.getSession(cube).getDatabase(), (Table) (cube.getFact()));
                SQLColumn defaultFK;
                // If there is a relationship between the fact table and the dimension table,
                // by default, the usage takes the mapped foreign key.
                if (!factTable.getExportedKeys().isEmpty()) {
                    SQLRelationship relationship = factTable.getExportedKeys().get(0);
                    List<ColumnMapping> mappings = relationship.getMappings();
                    defaultFK = mappings.get(0).getFkColumn();
                    du.setForeignKey(defaultFK.toString());
                    dimension.setForeignKey(defaultFK.toString());
                    // Further set all the hierarchy primary key to default value.
                    for (Hierarchy hierarchy : dimension.getHierarchies()) {
                        hierarchy.setPrimaryKey(defaultFK.toString());
                    }
                } else {

                    final DataEntryPanel mep = new DimensionUsageEditPanel(du, dimension);
                    Callable<Boolean> okCall = new Callable<Boolean>() {
                        public Boolean call() throws Exception {
                            // Further set all the hierarchy primary key to default value on the first run.
                            for (Hierarchy hierarchy : dimension.getHierarchies()) {
                                hierarchy.setPrimaryKey(dimension.getForeignKey());
                            }
                            return mep.applyChanges();
                        }
                    };
                    Callable<Boolean> cancelCall = new Callable<Boolean>() {
                        public Boolean call() throws Exception {
                            du.getParent().removeChild(du);
                            return true;
                        }
                    };
                    JDialog d = DataEntryPanelBuilder.createDataEntryPanelDialog(
                            mep,
                            SwingUtilities.getWindowAncestor(playpen),
                            "Dimension Usage Properties",
                            "OK",
                            okCall,
                            cancelCall);
                    d.setLocationRelativeTo(playpen);
                    d.setVisible(true);
                }
            } catch (ArchitectException e) {
                throw new ArchitectRuntimeException(e);
            }
        } else {
            String errorMsg = "Cube Dimension \"" + dimension.getName() + "\" already exists in \"" +
                    cp.getModel().getName() + "\".\nDimension Usage was not created.";
            JOptionPane.showMessageDialog(playpen, errorMsg, "Duplicate Cube Dimension", JOptionPane.INFORMATION_MESSAGE);
        }
    }

}
