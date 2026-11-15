package com.sidebartoggle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class SidebarTogglePanel extends PluginPanel
{
    private final JPanel rows = new JPanel();
    private final JLabel status = new JLabel("Discovering sidebar icons…");
    private final JTextField search = new JTextField();
    private final BiConsumer<String, Boolean> toggle;
    private final BiConsumer<String, String> moveBefore;
    private boolean dragging;
    private boolean dragMoved;
    private boolean dropValid;
    private String dropKey;
    private JPanel dropRow;
    private String signature = "";
    private List<SidebarTogglePlugin.Entry> entries = java.util.Collections.emptyList();
    private Set<String> hidden = java.util.Collections.emptySet();

    SidebarTogglePanel(BiConsumer<String, Boolean> toggle, Runnable showAll, Runnable refresh)
    {
        this(toggle, showAll, refresh, (key, before) -> { }, () -> { });
    }

    SidebarTogglePanel(BiConsumer<String, Boolean> toggle, Runnable showAll, Runnable refresh,
        BiConsumer<String, String> moveBefore, Runnable resetOrder)
    {
        this.toggle = toggle;
        this.moveBefore = moveBefore;
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        JPanel header = new JPanel(new GridBagLayout());
        JLabel title = new JLabel("Sidebar Icons");
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        addHeader(header, title, 8);
        addHeader(header, new JLabel("<html>Uncheck an icon to hide it.<br>Its plugin keeps running.</html>"), 12);
        search.setToolTipText("Filter icons. Enable a plugin to discover its sidebar items.");
        search.putClientProperty("JTextField.placeholderText", "Filter icons…");
        search.setPreferredSize(new Dimension(0, 30));
        search.setMinimumSize(new Dimension(0, 30));
        addHeader(header, search, 8);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.setPreferredSize(new Dimension(0, 28));
        JButton restore = new JButton("Show all");
        restore.addActionListener(event -> showAll.run());
        JButton rescan = new JButton("Refresh");
        rescan.addActionListener(event -> refresh.run());
        buttons.add(restore); buttons.add(rescan);
        JButton reset = new JButton("Reset order");
        reset.addActionListener(event -> resetOrder.run());
        addHeader(header, buttons, 6);
        reset.setPreferredSize(new Dimension(0, 28));
        addHeader(header, reset, 12);
        addHeader(header, status, 8);
        addHeader(header, new JLabel("<html>Drag the grip to reorder.<br>New icons appear automatically.</html>"), 0);
        add(header, BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        add(rows, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { rebuild(); }
            public void removeUpdate(DocumentEvent event) { rebuild(); }
            public void changedUpdate(DocumentEvent event) { rebuild(); }
        });
    }

    private static void addHeader(JPanel header, java.awt.Component component, int bottomGap)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = GridBagConstraints.RELATIVE;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, bottomGap, 0);
        header.add(component, constraints);
    }

    void update(List<SidebarTogglePlugin.Entry> entries, Set<String> hidden)
    {
        this.entries = entries;
        this.hidden = new java.util.HashSet<>(hidden);
        StringBuilder next = new StringBuilder();
        for (SidebarTogglePlugin.Entry entry : entries) { next.append(entry.key()).append(entry.label()).append(hidden.contains(entry.key())); }
        if (!signature.equals(next.toString())) { signature = next.toString(); rebuild(); }
        long count = entries.stream().filter(entry -> hidden.contains(entry.key())).count();
        status.setForeground(Color.LIGHT_GRAY);
        status.setText(entries.size() + " icons · " + count + " hidden");
    }

    private void rebuild()
    {
        if (dragging) { return; }
        rows.removeAll();
        String query = search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        Boolean actionGroup = null;
        for (SidebarTogglePlugin.Entry entry : entries)
        {
            if (!entry.label().toLowerCase(java.util.Locale.ROOT).contains(query)) { continue; }
            if (actionGroup == null || actionGroup != entry.isAction())
            {
                JLabel heading = new JLabel(entry.isAction() ? "Toolbar actions" : "Sidebar panels");
                heading.setForeground(ColorScheme.BRAND_ORANGE);
                heading.setFont(heading.getFont().deriveFont(Font.BOLD));
                heading.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
                heading.setAlignmentX(LEFT_ALIGNMENT);
                rows.add(heading);
                actionGroup = entry.isAction();
            }
            JPanel row = new JPanel(new BorderLayout(5, 0));
            row.putClientProperty("entryKey", entry.key());
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setPreferredSize(new Dimension(0, 32));
            row.setMinimumSize(new Dimension(0, 32));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            row.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            JLabel grip = new JLabel("☰");
            grip.setForeground(Color.LIGHT_GRAY);
            grip.setToolTipText("Drag to reorder");
            grip.setPreferredSize(new Dimension(20, 24));
            grip.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
            JCheckBox box = new JCheckBox(entry.label(), !hidden.contains(entry.key()));
            box.setOpaque(false);
            box.setToolTipText(entry.label());
            box.addActionListener(event -> toggle.accept(entry.key(), !box.isSelected()));
            row.add(grip, BorderLayout.WEST);
            row.add(box, BorderLayout.CENTER);
            installDrag(grip, row, entry);
            rows.add(row);
        }
        if (rows.getComponentCount() == 0) { rows.add(new JLabel("No matching sidebar icons.")); }
        rows.revalidate(); rows.repaint();
    }

    private void installDrag(JLabel grip, JPanel row, SidebarTogglePlugin.Entry entry)
    {
        MouseAdapter drag = new MouseAdapter() {
            private Point start;
            @Override public void mousePressed(MouseEvent event)
            {
                if (!SwingUtilities.isLeftMouseButton(event)) { return; }
                dragging = true;
                dragMoved = false;
                dropValid = false;
                start = event.getPoint();
                setDropIndicator(null, false);
            }
            @Override public void mouseReleased(MouseEvent event)
            {
                if (!dragging) { return; }
                String before = dropKey;
                boolean commit = dragMoved && dropValid;
                dragging = false;
                setDropIndicator(null, false);
                if (commit) { moveBefore.accept(entry.key(), before); }
                rebuild();
            }
            @Override public void mouseDragged(MouseEvent event)
            {
                if (!dragging) { return; }
                if (!dragMoved && start.distance(event.getPoint()) < 4) { return; }
                dragMoved = true;
                Point point = SwingUtilities.convertPoint(grip, event.getPoint(), rows);
                JPanel target = null, last = row;
                int top = row.getY(), bottom = row.getY() + row.getHeight();
                for (java.awt.Component component : rows.getComponents())
                {
                    if (!(component instanceof JPanel)) { continue; }
                    JPanel candidateRow = (JPanel) component;
                    SidebarTogglePlugin.Entry candidate = entryForRow(candidateRow);
                    if (candidate == null || candidate.isAction() != entry.isAction()) { continue; }
                    top = Math.min(top, candidateRow.getY());
                    bottom = Math.max(bottom, candidateRow.getY() + candidateRow.getHeight());
                    if (candidateRow.getY() >= last.getY()) { last = candidateRow; }
                    if (target == null && candidateRow != row && point.y < candidateRow.getY() + candidateRow.getHeight() / 2)
                    { target = candidateRow; }
                }
                dropValid = point.x >= 0 && point.x <= rows.getWidth() && point.y >= top - 8 && point.y <= bottom + 8;
                setDropIndicator(dropValid ? target == null ? last : target : null, target == null);
                if (dropValid && target == null) { dropKey = null; }
                rows.scrollRectToVisible(new java.awt.Rectangle(point.x, Math.max(0, point.y - 15), 1, 30));
            }
        };
        grip.addMouseListener(drag);
        grip.addMouseMotionListener(drag);
    }

    private SidebarTogglePlugin.Entry entryForRow(JPanel row)
    {
        for (SidebarTogglePlugin.Entry entry : entries)
        {
            if (row.getClientProperty("entryKey") != null && entry.key().equals(row.getClientProperty("entryKey"))) { return entry; }
        }
        return null;
    }

    private void setDropIndicator(JPanel target, boolean after)
    {
        if (dropRow != null) { dropRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0)); }
        dropRow = target;
        dropKey = null;
        if (target != null)
        {
            target.setBorder(BorderFactory.createMatteBorder(after ? 0 : 2, 0, after ? 2 : 0, 0, ColorScheme.BRAND_ORANGE));
            dropKey = (String) target.getClientProperty("entryKey");
        }
    }

    void error(String message)
    {
        status.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        status.setText("<html>" + message + "</html>");
    }
}
