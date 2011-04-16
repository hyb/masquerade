package masquerade.sim.ui;

import java.util.Collection;
import java.util.LinkedHashSet;

import masquerade.sim.DeleteListener;
import masquerade.sim.UpdateListener;
import masquerade.sim.util.ClassUtil;

import com.vaadin.data.Container;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.Validator.InvalidValueException;
import com.vaadin.data.util.BeanItem;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.ComponentContainer;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.DefaultFieldFactory;
import com.vaadin.ui.Form;
import com.vaadin.ui.FormFieldFactory;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Table;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.Reindeer;

public class MasterDetailView extends CustomComponent {

	private static final String TABLE_WIDTH = "400px";

	public interface DetailViewUpdateListener {
		/**
		 * 
		 * @param selectedMasterObject Selected object in the master table
		 * @param activeDetailView Currently active detail view, or <code>null</code> if none
		 * @return The detail view to use for the selected object (either the same as activeDetailView, or a new view), or <code>null</code> to remove the detail view
		 */
		Component onUpdateDetailView(Object selectedMasterObject, Component activeDetailView);
	}
	
	public interface AddListener {
		void onAdd();
	}

	private ComponentContainer mainContainer;
    private ComponentContainer detailContainer;
    private Table masterTable;
	private DetailViewUpdateListener detailViewUpdateListener = createDefaultDetailViewUpdateListener();
	private Component detailView;
	private FormFieldFactory fieldFactory;
	private Collection<UpdateListener> formCommitListeners = new LinkedHashSet<UpdateListener>();
	private Collection<AddListener> addListeners = new LinkedHashSet<AddListener>();
	private Collection<DeleteListener> deleteListeners = new LinkedHashSet<DeleteListener>();
	private boolean isWriteThrough = false;
	private VerticalLayout leftLayout;

	public MasterDetailView() {
		this(DefaultFieldFactory.get());
	}

	/**
	 * The constructor builds the main layout, sets the
	 * composition root and then does any custom initialization.
	 *
	 * The constructor will not be automatically regenerated by the
	 * visual editor.
	 */
	public MasterDetailView(FormFieldFactory fieldFactory) {
		buildMainLayout();
		setCompositionRoot(mainContainer);
		this.fieldFactory = fieldFactory;
	}
	
	public void setWriteThrough(boolean flag) {
		isWriteThrough = flag;
	}
	
	// TODO: Move visibleCols to c'tor or own setter
	public void setDataSource(Container dataSource, String[] visibleColumns) {
		masterTable.setContainerDataSource(dataSource);
		if (visibleColumns != null) {
			masterTable.setVisibleColumns(visibleColumns);
		}
	}
	
	public Container getDataSource() {
		return masterTable.getContainerDataSource();
	}

	public void setSelection(Object itemId) {
		masterTable.select(itemId);
	}
	
	/**
	 * Override the default detail view update listener, which creates a form based on the
	 * selected master object.
	 * @param listener
	 */
	public void setDetailViewUpdateListener(DetailViewUpdateListener listener) {
		detailViewUpdateListener = listener;
	}

	public void addFormCommitListener(UpdateListener formCommitListener) {
		formCommitListeners.add(formCommitListener);
	}

	public void removeFormCommitListener(UpdateListener formCommitListener) {
		formCommitListeners.remove(formCommitListener);
	}

	public void addAddListener(AddListener addListener) {
		addListeners.add(addListener);
	}
	
	public void removeAddListener(AddListener addListener) {
		addListeners.remove(addListener);
	}

	public void addDeleteListener(DeleteListener deleteListener) {
		deleteListeners .add(deleteListener);
	}
	
	public void removeCreateListener(DeleteListener deleteListener) {
		deleteListeners.remove(deleteListener);
	}
	
	public void setMasterTableWidth(String width) {
		masterTable.setWidth(width);
		leftLayout.setWidth(width);
	}

	private DetailViewUpdateListener createDefaultDetailViewUpdateListener() {
		return new DetailViewUpdateListener() {
			@Override
			public Component onUpdateDetailView(Object selectedMasterObject, Component activeDetailView) {
				if (selectedMasterObject == null) {
					return null;
				}
				
				return createForm(selectedMasterObject);
			}
		};
	}
	
	private void buildMainLayout() {
	    // Create layout
	    HorizontalLayout mainLayout = new HorizontalLayout();
	    
	    // Top-level component properties
	    setSizeFull();
	    
	    leftLayout = new VerticalLayout();
	    leftLayout.setHeight("100%");
	    
	    // MasterTable
        masterTable = new Table(null, null);
        masterTable.setSelectable(true);
        masterTable.setImmediate(true);
        masterTable.setNullSelectionAllowed(true);
        masterTable.setWidth(TABLE_WIDTH);
        masterTable.setHeight("100%");
        masterTable.addListener(new ValueChangeListener() {
			@Override
			public void valueChange(ValueChangeEvent event) {
				updateDetailView(event.getProperty().getValue());
			}
		});
        leftLayout.addComponent(masterTable);
        leftLayout.setExpandRatio(masterTable, 1.0f);

        // Add button
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setSpacing(true);
        buttonLayout.setMargin(true, false, false, false);
        Button addButton = new Button("Add");
        addButton.addListener(new ClickListener() {
			@Override
			public void buttonClick(ClickEvent event) {
				fireAdd();
			}
		});
		buttonLayout.addComponent(addButton);

        // Remove button
        final Button removeButton = new Button("Remove");
        removeButton.setEnabled(false);
        removeButton.addListener(new ClickListener() {
			@Override
			public void buttonClick(ClickEvent event) {
				setDetailView(null);
				removeButton.setEnabled(false);
				fireDeleteObject(masterTable.getValue());
			}
		});
        masterTable.addListener(new ValueChangeListener() {
			@Override
			public void valueChange(ValueChangeEvent event) {
				removeButton.setEnabled(event.getProperty().getValue() != null);
			}
		});
		buttonLayout.addComponent(removeButton);
        
        leftLayout.addComponent(buttonLayout);
        leftLayout.setWidth(TABLE_WIDTH);
	    mainLayout.addComponent(leftLayout);
	    
	    // DetailLayout
	    GridLayout detailLayout = new GridLayout();
	    detailLayout.setSizeFull();
	    detailLayout.setMargin(false, false, false, true);
	    mainLayout.addComponent(detailLayout);

	    // Wrap form into a panel to get scroll bars if it cannot be displayed fully
	    Panel panel = new Panel();
		panel.setSizeFull();
		panel.addStyleName(Reindeer.PANEL_LIGHT);
		detailLayout.addComponent(panel);
	    
	    mainLayout.setExpandRatio(detailLayout, 1.0f);
	    
	    mainLayout.setSizeFull();
	    mainLayout.setSpacing(false);
	    
	    detailContainer = panel;
	    mainContainer = mainLayout;
    }

	private void updateDetailView(Object value) {
		if (detailViewUpdateListener != null) {
			Component oldView = detailView;
			Component newView = detailViewUpdateListener.onUpdateDetailView(value, detailView);
			if (oldView != newView) {
				setDetailView(newView);
			}
		}
    }
	
	private void setDetailView(Component component) {
		detailContainer.removeAllComponents();
		if (component != null) {
			// Wrap it into a 
			detailContainer.addComponent(component);
		}
		detailView = component;
	}	
    
	private Component createForm(Object bean) {
		final Form form = new Form();
		form.setSizeFull();
		final String shortTypeName = ClassUtil.unqualifiedName(bean);
		form.setCaption(shortTypeName);
		form.setWriteThrough(isWriteThrough );
        form.setInvalidCommitted(false); // no invalid values in datamodel
		form.setFormFieldFactory(fieldFactory);

		BeanItem<?> item = new BeanItem<Object>(bean);
		form.setItemDataSource(item);
		
        form.getLayout().setWidth(TABLE_WIDTH);
        
        // Add apply button in form footer
        HorizontalLayout buttons = new HorizontalLayout();
		Button apply = new Button("Save", new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                try {
                    form.commit();
                    
                    BeanItem<?> item = (BeanItem<?>) form.getItemDataSource();
                    fireFormCommited(item.getBean());
                    
					getWindow().showNotification("Saved", shortTypeName + " updated");
                } catch (InvalidValueException e) {
                    // Ignored, we'll let the Form handle the errors
                }
            }
        });
		buttons.addComponent(apply);
		buttons.setComponentAlignment(apply, Alignment.MIDDLE_LEFT);
		form.getFooter().addComponent(buttons);
		form.getFooter().setMargin(true, true, false, false);
		
		return form;
	}

	private void fireFormCommited(Object bean) {
		for (UpdateListener listener : formCommitListeners) {
            listener.notifyUpdated(bean);
        }
    }

	private void fireAdd() {
		for (AddListener listener : addListeners ) {
			listener.onAdd();
		}
    }

	private void fireDeleteObject(Object bean) {
		if (bean == null) {
			return;
		}
		
		for (DeleteListener listener : deleteListeners ) {
			listener.notifyDelete(bean);
		}
    }
}
