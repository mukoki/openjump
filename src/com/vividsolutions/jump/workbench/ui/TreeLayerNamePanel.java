/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI for
 * visualizing and manipulating spatial features with geometry and attributes.
 * 
 * Copyright (C) 2003 Vivid Solutions
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * For more information, contact:
 *
 * Vivid Solutions
 * Suite #1A
 * 2328 Government Street
 * Victoria BC  V8T 5G5
 * Canada
 *
 * (250)385-6040
 * www.vividsolutions.com
 */
package com.vividsolutions.jump.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.openjump.core.rasterimage.RasterImageLayer;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.util.Assert;
import com.vividsolutions.jump.util.CollectionUtil;
import com.vividsolutions.jump.util.LangUtil;
import com.vividsolutions.jump.workbench.model.Category;
import com.vividsolutions.jump.workbench.model.CategoryEvent;
import com.vividsolutions.jump.workbench.model.CategoryEventType;
import com.vividsolutions.jump.workbench.model.FeatureEvent;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.LayerEvent;
import com.vividsolutions.jump.workbench.model.LayerEventType;
import com.vividsolutions.jump.workbench.model.LayerListener;
import com.vividsolutions.jump.workbench.model.LayerManager;
import com.vividsolutions.jump.workbench.model.LayerManagerProxy;
import com.vividsolutions.jump.workbench.model.LayerTreeModel;
import com.vividsolutions.jump.workbench.model.LayerTreeModel.RasterStyleValueIntv;
import com.vividsolutions.jump.workbench.model.LayerTreeModel.RasterStyleValueRamp;
import com.vividsolutions.jump.workbench.model.Layerable;
import com.vividsolutions.jump.workbench.model.WMSLayer;
import com.vividsolutions.jump.workbench.ui.renderer.RenderingManager;
import com.vividsolutions.jump.workbench.ui.renderer.style.BasicStyle;

import org.openjump.core.rasterimage.RasterSymbology;

public class TreeLayerNamePanel extends JPanel implements LayerListener,
    LayerableNamePanel, LayerNamePanelProxy, PopupNodeProxy {

  final private Map<Class<?>,JPopupMenu> nodeClassToPopupMenuMap = new HashMap<>();

  JTree tree = new JTree() {

    @Override
    public boolean isPathEditable(TreePath path) {
      if (!isEditable()) {
        return false;
      }

      return path.getLastPathComponent() instanceof Layerable
          || path.getLastPathComponent() instanceof Category;
    }


    // [ede 12.2012] only allow plain cursor keys. during adding shortcuts it
    // became obvious that the JTree reacted on each and every keystroke.
    // e.g. CTRL+A clashed with the SelectAllLayerItems shortcut as it selected
    // all layers in tree although the shortcut is meant to select all items in
    // all _selected_ layers only
    final List<Integer> allowedKeys = Arrays.asList(KeyEvent.VK_LEFT,
            KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN);
    @Override
    protected void processComponentKeyEvent(KeyEvent e) {
      // filter accepted key events
      if (e.getModifiersEx() != 0 || !allowedKeys.contains(e.getKeyCode()))
        return;
      super.processComponentKeyEvent(e);
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }

    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e,
        int condition, boolean pressed) {
      // filter accepted key events
      if (e.getModifiersEx() != 0 || !allowedKeys.contains(e.getKeyCode()))
        return false;
      return super.processKeyBinding(ks, e, condition, pressed);
    }

  };

  final private LayerTreeCellRenderer layerTreeCellRenderer;

  //private TreeCellEditor cellEditor = new LayerTreeCellEditor(tree);

  private Object popupNode;

  final private ArrayList<LayerNamePanelListener> listeners = new ArrayList<>();

  final private LayerManagerProxy layerManagerProxy;

  final private FirableTreeModelWrapper firableTreeModelWrapper;

  // used to drag Layerables among Categories
  private TreePath movingTreePath = null;

  private boolean firstTimeDragging = true;
  private boolean ongoingDragging = false;

  private int lastHoveringRow = -1;

  /**
     * @param layerManagerProxy a LayerManager proxy
     * @param treeModel the TreeModel
     * @param renderingManager rendering manager
     * @param additionalNodeClassToTreeCellRendererMap map associating
     *        Tree node classes to TreeCellRenderer
     */
  public TreeLayerNamePanel(LayerManagerProxy layerManagerProxy,
      TreeModel treeModel, RenderingManager renderingManager,
      Map<Class<?>,TreeCellRenderer> additionalNodeClassToTreeCellRendererMap) {

    layerManagerProxy.getLayerManager().addLayerListener(this);
    this.layerManagerProxy = layerManagerProxy;

    try {
      jbInit();
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    firableTreeModelWrapper = new FirableTreeModelWrapper(treeModel);
    tree.setModel(firableTreeModelWrapper);
    layerTreeCellRenderer = new LayerTreeCellRenderer(renderingManager);
    renderingManager.getPanel().getViewport()
        .addListener(new ViewportListener() {
          public void zoomChanged(Envelope modelEnvelope) {
            // After a zoom, the scale may be outside the visible
            // scale range for one or more layers, in which case we
            // want to update the layer names to be grey. So
            // repaint. [Jon Aquino 2005-03-10]
            TreeLayerNamePanel.this.repaint();
          }
        });
    setCellRenderer(additionalNodeClassToTreeCellRendererMap);
    tree.getSelectionModel().setSelectionMode(
        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    tree.addMouseListener(new MouseAdapter() {

      public void mouseClicked(MouseEvent e) {
        handleCheckBoxClick(e);
      }

      public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          ongoingDragging = true;
          movingTreePath = tree.getPathForLocation(e.getX(), e.getY());
          // move only Layerables, not Categories
          if (movingTreePath != null
              && !(movingTreePath.getLastPathComponent() instanceof Layerable)) {
            movingTreePath = null;
          } else if (movingTreePath != null
              && !tree.isRowSelected(tree.getClosestRowForLocation(e.getX(),
                  e.getY()))) {
            movingTreePath = null;
          } else {
            lastHoveringRow = tree.getClosestRowForLocation(e.getX(), e.getY());
          }
        } else {
          movingTreePath = null;
        }
      }

      public void mouseReleased(MouseEvent e) {
        
        if (e.getButton() != MouseEvent.BUTTON1 || movingTreePath == null) {
          return;
        }

        if (!ongoingDragging) {
          return;
        }
        
        // mouseup _always_ finishes the drag, fixes bug #400 "Layer change category untimely"
        ongoingDragging = false;

        if (tree.isEditing()) {
            return;
        }

        Object node = movingTreePath.getLastPathComponent();
        TreePath tpDestination = tree.getClosestPathForLocation(e.getX(),
            e.getY());

        // Fix: When dragging a Layerable onto a Category, and then
        // selecting a different layer, the last XOR placement of the
        // dragbar would appear over the Category. Need to reset
        // firstTimeDragging to true before returning.
        firstTimeDragging = true;
        lastHoveringRow = -1;

        if (tpDestination == null) {
          return;
        }

        // remove remnants of horizontal drag bar by refreshing display
        tree.repaint();
        // Changed #update to #repaint -- less flickery for some reason
        // [Jon Aquino 2004-03-17]

        // dragging a layerable
        if (node instanceof Layerable) {
          Layerable layerable = (Layerable) node;
          int index = 0;
          Category cat;

          int oldRow = tree.getRowForPath(movingTreePath);
          int newRow = tree.getRowForPath(tpDestination);
          Category oldCat = (Category) movingTreePath.getParentPath()
              .getLastPathComponent();

          if (tpDestination.getLastPathComponent() instanceof Layerable) {

            // Fix: When shift-clicking to select a range of nodes,
            // last node would unselect because the layer would get
            // removed then re-added. [Jon Aquino 2004-03-11]
            if (layerable == tpDestination.getLastPathComponent()) {
              return;
            }

            cat = getLayerManager().getCategory(
                (Layerable) tpDestination.getLastPathComponent());

            index = tree.getModel().getIndexOfChild(
                tpDestination.getParentPath().getLastPathComponent(),
                tpDestination.getLastPathComponent());
            // adjust where the Layer will be drop exactly
            if (newRow < oldRow && cat.equals(oldCat))
              index++;
            else if (!cat.equals(oldCat))
              index++;
          } else if (tpDestination.getLastPathComponent() instanceof Category) {
            cat = (Category) tpDestination.getLastPathComponent();
            // Prevent unnecessary removals and re-additions
            // [Jon Aquino 2004-03-11]
            // if (cat.contains(layerable)) {
            // return;
            // }
          } else {
            // Can get here if the node is, for example,
            // a LayerTreeModel.ColorThemingValue [Jon Aquino 2005-07-25]
            return;
          }
          //getLayerManager().remove(layerable);
          getLayerManager().fireLayerChanged(layerable, LayerEventType.REMOVED);
          oldCat.remove(layerable);
          cat.add(index, layerable);
          getLayerManager().fireLayerChanged(layerable, LayerEventType.ADDED);
          movingTreePath = null;
        }
      }
    });

    tree.addMouseMotionListener(new MouseMotionAdapter() {
      int rowNew;

      Rectangle dragBar;

      public void mouseDragged(MouseEvent e) {
        // return if mouse is dragged while not originating on a tree node
        if (movingTreePath == null) {
          firstTimeDragging = true;
          return;
        }

        if (tree.isEditing()) {
            return;
        }

        // int rowOld = tree.getRowForPath(movingTreePath);
        rowNew = tree.getClosestRowForLocation(e.getX(), e.getY());
        // rowOld = tree.getRowForPath(movingTreePath);
        // if the dragging of a row hasn't moved outside of the bounds
        // of the currently selected row, don't show the horizontal drag
        // bar.
        //if (rowNew == lastHoveringRow/* || rowNew == rowOld - 1
        //    || rowNew == rowOld*/) {
        //  return;
        //}

        //[Giuseppe Aruta 2019-01-06] as suggested by Roberto Rossi (University of Padua) also RasterImageLayer don't need to be expanded when
        // dragged on layerable tree
        if (!(tree.getPathForRow(rowNew).getLastPathComponent() instanceof Layer) &&
            !(tree.getPathForRow(rowNew).getLastPathComponent() instanceof RasterImageLayer)) {
          tree.expandRow(rowNew);
        }

        Graphics2D g2 = (Graphics2D) tree.getGraphics();
        g2.setColor(Color.RED);
        g2.setXORMode(Color.WHITE);
        // if this is the first time moving the dragbar, draw the
        // dragbar so XOR drawing works properly
        if (firstTimeDragging) {
          dragBar = new Rectangle(0, 0, tree.getWidth(), 3);
          dragBar.setLocation(
              0,
              tree.getRowBounds(lastHoveringRow).y
                  + tree.getRowBounds(lastHoveringRow).height - 3);
          g2.fill(dragBar);
          firstTimeDragging = false;
        }
        // XOR drawing mode of horizontal drag bar
        else if (rowNew != lastHoveringRow) {
            g2.setXORMode(Color.WHITE);
            g2.fill(dragBar);
            dragBar.setLocation(0,
                    tree.getRowBounds(rowNew).y + tree.getRowBounds(rowNew).height - 3);
            g2.fill(dragBar);
            lastHoveringRow = rowNew;
        }
      }
    });

    tree.setCellEditor(new LayerTreeCellEditor());
    tree.setInvokesStopCellEditing(true);
    tree.setBackground(getBackground());
    tree.addTreeSelectionListener(e -> fireLayerSelectionChanged());
    tree.getModel().addTreeModelListener(new TreeModelListener() {

      public void treeNodesChanged(TreeModelEvent e) { }

      public void treeNodesInserted(TreeModelEvent e) {
        for (int i = 0; i < e.getChildren().length; i++) {
          TreeUtil.visit(tree.getModel(), e.getTreePath().pathByAddingChild(e.getChildren()[i]), path -> {
            // When opening a task file, don't expand the ColorThemingValues [Jon Aquino 2005-08-01]
            if (path.peek() instanceof LayerTreeModel.SymbolizationLeafNode) {
              return;
            }
            tree.makeVisible(new TreePath(path.toArray()));
          });
        }
      }

      public void treeNodesRemoved(TreeModelEvent e) { }

      public void treeStructureChanged(TreeModelEvent e) { }

    });
    TreeUtil.expandAll(tree, new TreePath(tree.getModel().getRoot()));
  }

  public void addPopupMenu(Class<?> nodeClass, JPopupMenu popupMenu) {
    nodeClassToPopupMenuMap.put(nodeClass, popupMenu);
  }

  private void setCellRenderer(Map<Class<?>,TreeCellRenderer> additionalNodeClassToTreeCellRendererMap) {
    final Map<Class<?>,TreeCellRenderer> treeCellRendererMap = createNodeClassToTreeCellRendererMap();
    treeCellRendererMap.putAll(additionalNodeClassToTreeCellRendererMap);
    tree.setCellRenderer(new TreeCellRenderer() {
      private final DefaultTreeCellRenderer defaultRenderer = new DefaultTreeCellRenderer() {

        {
          // Transparent. [Jon Aquino]
          setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
        }
      };

      @Override
      public Component getTreeCellRendererComponent(JTree tree, Object value,
          boolean selected, boolean expanded, boolean leaf, int row,
          boolean hasFocus) {
        // Return the component
        return ((TreeCellRenderer) LangUtil.ifNull(
            CollectionUtil.get(value.getClass(), treeCellRendererMap), defaultRenderer))
            .getTreeCellRendererComponent(tree, value, selected, expanded,
                leaf, row, hasFocus);
      }
    });
  }

  private Map<Class<?>,TreeCellRenderer> createNodeClassToTreeCellRendererMap() {
    Map<Class<?>,TreeCellRenderer> map = new HashMap<>();
    map.put(Layer.class, layerTreeCellRenderer);
    map.put(WMSLayer.class, layerTreeCellRenderer);
    map.put(Category.class, layerTreeCellRenderer);
    map.put(RasterImageLayer.class, layerTreeCellRenderer);
    map.put(LayerTreeModel.ColorThemingValue.class, createColorThemingValueRenderer());
    map.put(LayerTreeModel.RasterStyleValueIntv.class, createRasterStyleValueIntvRenderer());
    map.put(LayerTreeModel.RasterStyleValueRamp.class, createRasterStyleValueRampRenderer());
    return map;
  }

  private TreeCellRenderer createColorThemingValueRenderer() {
    return new TreeCellRenderer() {
      private final JPanel panel = new JPanel(new GridBagLayout());
      private final ColorPanel colorPanel = new ColorPanel();
      private final JLabel label = new JLabel();
      {
        panel.add(colorPanel, new GridBagConstraints(0, 0, 1, 1, 0, 0,
            GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0,
                0, 0), 0, 0));
        panel.add(label, new GridBagConstraints(1, 0, 1, 1, 0, 0,
            GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5,
                0, 0), 0, 0));
      }

      @Override
      public Component getTreeCellRendererComponent(JTree tree, Object value,
          boolean selected, boolean expanded, boolean leaf, int row,
          boolean hasFocus) {
        label.setText(value.toString());
        BasicStyle style = ((LayerTreeModel.ColorThemingValue) value)
            .getStyle();
        colorPanel.setLineColor(style.isRenderingLine() ? GUIUtil.alphaColor(
            style.getLineColor(), style.getAlpha()) : GUIUtil.alphaColor(
            Color.BLACK, 0));
        colorPanel.setFillColor(style.isRenderingFill() ? GUIUtil.alphaColor(
            style.getFillColor(), style.getAlpha()) : GUIUtil.alphaColor(
            Color.BLACK, 0));
        return panel;
      }
    };
  }

  private TreeCellRenderer createRasterStyleValueIntvRenderer() {
        
    return new TreeCellRenderer() {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final ColorPanel colorPanel = new ColorPanel();
        private final JLabel label = new JLabel();
        {
            panel.add(colorPanel,
                    new GridBagConstraints(
                            0, 0, 1, 1, 0, 0,
                            GridBagConstraints.WEST,
                            GridBagConstraints.NONE,
                            new Insets(0,0,0,0),
                            0, 0));
            panel.add(label,
                    new GridBagConstraints(
                            1, 0, 1, 1, 0, 0,
                            GridBagConstraints.WEST,
                            GridBagConstraints.NONE,
                            new Insets(0,5,0,0),
                            0, 0));
        }
            
        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {

            RasterStyleValueIntv rasterStyleValue = (RasterStyleValueIntv) value;
            
            if(rasterStyleValue.getColorMapType().equals(RasterSymbology.TYPE_INTERVALS)) {
                label.setText(
                        rasterStyleValue.getValue().floatValue() + "-" +
                        rasterStyleValue.getNextValue().floatValue());
            } else if(rasterStyleValue.getColorMapType().equals(RasterSymbology.TYPE_SINGLE)) {
                label.setText(String.valueOf(rasterStyleValue.getValue().intValue()));
            }
            colorPanel.setLineColor(Color.BLACK);
            colorPanel.setFillColor(rasterStyleValue.getColor());      
            
            return panel;
        }
    };   
  }
  
  private TreeCellRenderer createRasterStyleValueRampRenderer() {
 
    return new TreeCellRenderer() {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JLabel labelTop = new JLabel();
        private final JLabel labelMiddle = new JLabel();
        private final JLabel labelBottom = new JLabel();
        private final JLabel labelImg = new JLabel();
        {
            
                panel.add(labelImg, new GridBagConstraints(0, 0, 1, 3, 0, 1,
                        GridBagConstraints.WEST,
                        GridBagConstraints.NONE,
                        new Insets(0,0,0,0),
                        0, 0));
                panel.add(labelTop, new GridBagConstraints(1, 0, 1, 1, 0, 1,
                        GridBagConstraints.WEST,
                        GridBagConstraints.VERTICAL,
                        new Insets(0,5,0,0),
                        0, 0));
                panel.add(labelMiddle, new GridBagConstraints(1, 1, 1, 1, 0, 1,
                        GridBagConstraints.WEST,
                        GridBagConstraints.VERTICAL,
                        new Insets(0,5,0,0),
                        0, 0));
                panel.add(labelBottom, new GridBagConstraints(1, 2, 1, 1, 0, 1,
                        GridBagConstraints.LAST_LINE_START,
                        GridBagConstraints.VERTICAL,
                        new Insets(0,5,0,0),
                        0, 0));
                
                labelTop.setVerticalAlignment(JLabel.TOP);
                labelBottom.setVerticalAlignment(JLabel.BOTTOM);
            
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            
            RasterStyleValueRamp rasterStyleValue = (RasterStyleValueRamp) value;
            
            labelTop.setText(String.valueOf(rasterStyleValue.getTopValue().floatValue()));
            labelMiddle.setText(String.valueOf(
                    (rasterStyleValue.getTopValue().floatValue() + rasterStyleValue.getBottomValue().floatValue())/2));
            labelBottom.setText(String.valueOf(rasterStyleValue.getBottomValue().floatValue()));  
            
            labelImg.setIcon(new RasterRampIcon(rasterStyleValue.getColors()));
            
            return panel;
        }
    };         
      
  }
  
  void jbInit() throws Exception {
    this.setLayout(new BorderLayout());
    JScrollPane scrollPane = new JScrollPane();
    tree.addMouseListener(new java.awt.event.MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        // popup triggers are pressed on Linux/OSX, released on Windows
        if (e.isPopupTrigger())
          tree_mouseReleased(e);
      }

      public void mouseReleased(MouseEvent e) {
        tree_mouseReleased(e);
      }
    });
    ToolTipManager.sharedInstance().registerComponent(tree);
    tree.setEditable(true);
    tree.setRootVisible(false);

    // Row height is set to -1 because otherwise, in Java 1.4, tree nodes
    // will be "chopped off" at the bottom [Jon Aquino]
    tree.setRowHeight(-1);
    scrollPane.getVerticalScrollBar().setUnitIncrement(20);
    tree.setShowsRootHandles(true);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(BorderFactory.createEtchedBorder());
    scrollPane.getViewport().add(tree);
    this.add(scrollPane, BorderLayout.CENTER);

    // [mmichaud 2013-11-10] Makes the TreeLayerName auto-scrollable
    tree.addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
            Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
            ((JComponent)e.getSource()).scrollRectToVisible(r);
        }
    });
  }

  void tree_mouseReleased(MouseEvent e) {
    // [ede 12.2012] use isPopupTrigger which is 
    // supposed to be _really_ crossplatform
    if (!e.isPopupTrigger()) {
      return;
    }

    TreePath popupPath = tree.getPathForLocation(e.getX(), e.getY());

    if (popupPath == null) {
      return;
    }

    popupNode = popupPath.getLastPathComponent();

    // #isAltDown returns true on a middle-click; #isMetaDown returns true
    // on a right-click[Jon Aquino]
    // [ede 12.2012] use isPopupTrigger which is supposed to be _really_
    // crossplatform, Swing utility methods give wrong results
    // Third check can't simply user JTree#isPathSelected because the node
    // wrappers are value objects and thus can't reliably be compared by
    // reference (which is what #isPathSelected seems to do). [Jon Aquino]
    if (e.isPopupTrigger() && !selectedNodes(Object.class).contains(popupNode)) {
      tree.getSelectionModel().clearSelection();
    }

    tree.getSelectionModel().addSelectionPath(popupPath);

    if (getPopupMenu(popupNode.getClass()) != null) {
      // place the popup 10px to the right as to circumvent accidental interaction with it
      getPopupMenu(popupNode.getClass()).show(e.getComponent(), e.getX()+10,
          e.getY());
    }
  }

  private JPopupMenu getPopupMenu(Class<?> nodeClass) {
    return (JPopupMenu) CollectionUtil.get(nodeClass, nodeClassToPopupMenuMap);
  }

  private void handleCheckBoxClick(MouseEvent e) {
    if (!SwingUtilities.isLeftMouseButton(e)) {
      return;
    }

    TreePath path = tree.getPathForLocation(e.getX(), e.getY());

    if (path == null) {
      return;
    }

    Object node = path.getLastPathComponent();

    if (!(node instanceof Layerable)) {
      return;
    }

    Layerable layerable = (Layerable) node;
    Point layerNodeLocation = tree.getUI().getPathBounds(tree, path)
        .getLocation();

    // Initialize the LayerNameRenderer with the current node.
    // checkBoxBounds will be different for Layers and WMSLayers. [Jon Aquino]
    layerTreeCellRenderer.getLayerNameRenderer().getTreeCellRendererComponent(
        tree, path.getLastPathComponent(), false, false, false, 0, false);

    Rectangle checkBoxBounds = layerTreeCellRenderer.getLayerNameRenderer()
        .getCheckBoxBounds();
    checkBoxBounds.translate((int) layerNodeLocation.getX(),
        (int) layerNodeLocation.getY());

    if (checkBoxBounds.contains(e.getPoint())) {
      layerable.setVisible(!layerable.isVisible());
    }
  }

  /**
   * Return an array of selected {@link Layer}s.
   * Note that layerables which are not Layers like WMSLayer or RasterImageLayer
   * are not returned.
   */
  public Layer[] getSelectedLayers() {
    return selectedLayers(this);
  }

  public static Layer[] selectedLayers(LayerNamePanel layerNamePanel) {
    return layerNamePanel.selectedNodes(Layer.class).toArray(new Layer[0]);
  }

  public static Layerable[] selectedLayerables(LayerNamePanel layerNamePanel) {
      return layerNamePanel.selectedNodes(Layer.class).toArray(new Layerable[0]);
  }
  
  public Collection<Layerable> getSelectedLayerables() {
    return selectedNodes(Layerable.class);
  }

  public Collection<Category> getSelectedCategories() {
    return selectedNodes(Category.class);
  }

  public <T> Collection<T> selectedNodes(Class<T> c) {
    return selectedNodes(c, tree);
  }

  public static <T> Collection<T> selectedNodes(Class<T> c, JTree tree) {
    ArrayList<T> selectedNodes = new ArrayList<>();
    TreePath[] selectionPaths = tree.getSelectionPaths();

    if (selectionPaths == null) {
      return new ArrayList<>();
    }

    for (TreePath path : selectionPaths) {
      Object node = path.getLastPathComponent();

      if (c.isInstance(node)) {
        selectedNodes.add((T)node);
      }
    }

    return selectedNodes;
  }

  public void setSelectedLayers(Layer[] layers) {
    tree.getSelectionModel().clearSelection();

    for (Layer layer : layers) {
      addSelectedLayer(layer);
    }
  }

  protected void addSelectedLayer(Layer layer) {
    tree.addSelectionPath(TreeUtil.findLayerTreePath(layer, tree.getModel()));
  }

  
  public void setSelectedLayerables(Layerable[] layers) {
      tree.getSelectionModel().clearSelection();

      for (final Layerable layer : layers) {
          addSelectedLayerable(layer);
      }
  }

  protected void addSelectedLayerable(Layerable layer) {
      tree.addSelectionPath(TreeUtil.findTreePath(layer, tree.getModel()));
  }
  
  
  public void layerChanged(final LayerEvent e) {
    final TreeModelEvent treeModelEvent = new TreeModelEvent(this,
        new Object[] { tree.getModel().getRoot(), e.getCategory() },
        new int[] { e.getLayerableIndex() }, new Object[] { e.getLayerable() });

    if (e.getType() == LayerEventType.ADDED) {
      firableTreeModelWrapper.fireTreeNodesInserted(treeModelEvent);
      // firing TreeStructureChanged solve the problem ID 3418067
      // But I did not find yet the true origin of the bug which is
      // probably somewhere else.
      // The strange thing is that the bug appears only in the cloned
      // windows, not in the original one [mmichaud 2011-10-16]
      // activate fireTreeStructureChanged ==> InvocationTargetException in
      // ClassifyAttributesPlugIn
      // deactivate fireTreeStructureChanged ==>
      firableTreeModelWrapper.fireTreeStructureChanged(treeModelEvent);
      if ((selectedNodes(Layerable.class)).size() == 0
          && e.getLayerable() instanceof Layer) {
        addSelectedLayer((Layer) e.getLayerable());
      }

      return;
    }

    if (e.getType() == LayerEventType.REMOVED) {
      firableTreeModelWrapper.fireTreeNodesRemoved(treeModelEvent);

      return;
    }

    if (e.getType() == LayerEventType.APPEARANCE_CHANGED) {
      // For some reason, if we don't use #invokeLater to call
      // #fireTreeStructureChanged,
      // blank lines get inserted into the JTree. For more information, see Java
      // Bug 4498762,
      // "When expandPath() is called by a JTree method, extra blank lines appear",
      // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4498762
      // [Jon Aquino 2005-07-25]
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          // Specify the path of the subtree rooted at the layer -- the
          // ColorThemingValues
          // may have changed. [Jon Aquino 2005-07-25]
          firableTreeModelWrapper.fireTreeStructureChanged(new TreeModelEvent(
              this, new Object[] { tree.getModel().getRoot(), e.getCategory(),
                  e.getLayerable() }));
        }
      });
      return;
    }

    if (e.getType() == LayerEventType.METADATA_CHANGED) {
      firableTreeModelWrapper.fireTreeNodesChanged(treeModelEvent);

      return;
    }

    if (e.getType() == LayerEventType.VISIBILITY_CHANGED) {
      firableTreeModelWrapper.fireTreeNodesChanged(treeModelEvent);

      return;
    }

    Assert.shouldNeverReachHere();
  }

  public void categoryChanged(CategoryEvent e) {
    TreeModelEvent treeModelEvent = new TreeModelEvent(this,
        new Object[] { tree.getModel().getRoot() },
        new int[] { e.getCategoryIndex() + indexOfFirstCategoryInTree() },
        new Object[] { e.getCategory() });

    if (e.getType() == CategoryEventType.ADDED) {
      firableTreeModelWrapper.fireTreeNodesInserted(treeModelEvent);

      return;
    }

    if (e.getType() == CategoryEventType.REMOVED) {
      firableTreeModelWrapper.fireTreeNodesRemoved(treeModelEvent);

      return;
    }

    if (e.getType() == CategoryEventType.METADATA_CHANGED) {
      firableTreeModelWrapper.fireTreeNodesChanged(treeModelEvent);

      return;
    }

    Assert.shouldNeverReachHere();
  }

  private int indexOfFirstCategoryInTree() {
    // Not 0 in ESE. [Jon Aquino]
    for (int i = 0; i < tree.getModel()
        .getChildCount(tree.getModel().getRoot()); i++) {
      if (tree.getModel().getChild(tree.getModel().getRoot(), i) instanceof Category) {
        return i;
      }
    }

    Assert.shouldNeverReachHere();

    return -1;
  }

  public void featuresChanged(FeatureEvent e) {
  }

  public void dispose() {
    // Layer events could still be fired after the TaskWindow containing
    // this LayerNamePanel is closed (e.g. by clones of the TaskWindow, or
    // by an attribute viewer). [Jon Aquino]
    layerManagerProxy.getLayerManager().removeLayerListener(this);
  }

  public JTree getTree() {
    return tree;
  }

  public synchronized void addListener(LayerNamePanelListener listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(LayerNamePanelListener listener) {
    listeners.remove(listener);
  }

  public synchronized void fireLayerSelectionChanged() {
    for (LayerNamePanelListener listener : listeners) {
      listener.layerSelectionChanged();
    }
  }

  public LayerManager getLayerManager() {
    return layerManagerProxy.getLayerManager();
  }

  public static Layer chooseEditableLayer(LayerNamePanel panel) {
    for (Layer layer : panel.getSelectedLayers()) {
      if (layer.isEditable() && layer.isVisible()) {
        return layer;
      }
    }
    for (Layer layer : panel.getLayerManager().getEditableLayers()) {
      if (layer.isVisible()) {
        return layer;
      }
    }
    return null;
  }

  public Layer chooseEditableLayer() {
    return chooseEditableLayer(this);
  }

  public LayerNamePanel getLayerNamePanel() {
    return this;
  }

  protected FirableTreeModelWrapper getFirableTreeModelWrapper() {
    return firableTreeModelWrapper;
  }

  public Object getPopupNode() {
    return popupNode;
  }

  protected LayerTreeCellRenderer getLayerTreeCellRenderer() {
    return layerTreeCellRenderer;
  }
  
}
