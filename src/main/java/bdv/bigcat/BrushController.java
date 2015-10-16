package bdv.bigcat;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;

public class BrushController<T extends Type<T>> implements MouseListener, MouseMotionListener {
	
	final protected ViewerPanel viewer;
	final protected Source<T> source;
	final protected RandomAccessibleInterval< T > labels;
	protected AffineTransform3D sourceTransform;
	// final protected AbstractSaturatedARGBStream colorStream;
	final protected RandomAccess< T > labelAccess;
	final protected T brushValue;
	protected int radius;
	protected boolean active;

	public BrushController(
			final ViewerPanel viewer,
			final Source< T > source,
			final T value,
			final int radius
			)
	{
		this.viewer = viewer;
		this.source = source;
		this.labels = source.getSource(0, 0);
		this.sourceTransform = new AffineTransform3D();
		this.brushValue = value;
		labelAccess = labels.randomAccess();
		this.radius = radius;
		this.active = false;
	}
	
	public void setRadius(int radius)
	{
		this.radius = radius;
	}
	
	public int getRadius()
	{
		return this.radius;
	}
	
	@Override
	public void mouseClicked(MouseEvent e)
	{
		if(e.isAltDown())
		{
			paint(e);
			viewer.requestRepaint();
		}
	}
	
	// TODO: Move this to a Brush class
	protected void paint(MouseEvent e)
	{
		double[] pos = new double[3];
		pos[0] = e.getX();
		pos[1] = e.getY();
		pos[2] = 0;
		
		double[] center = new double[3];
		
		viewer.displayToGlobalCoordinates( pos );
		source.getSourceTransform(0, 0, sourceTransform);
		sourceTransform.applyInverse(center, pos);
		
		// System.out.println( " Pos = {" + java.util.Arrays.toString(pos) + "}" );
		
		for(int i = -radius; i < radius + 1; ++i)
		{
			for(int j = -radius; j < radius + 1; ++j)
			{
				for(int k = -radius; k < radius + 1; ++k)
				{
					if(i*i + j*j + k*k >= radius*radius)
						continue;
					
					labelAccess.setPosition( (int) center[0] + i, 0 );
					labelAccess.setPosition( (int) center[1] + j, 1 );
					labelAccess.setPosition( (int) center[2] + k, 2 );		
					
					final T labelValues = labelAccess.get();
					// NOT SUPPORTED FOR SuperVoxelMultisetType
					try
					{
						labelValues.set(brushValue);
					}
					catch(ArrayIndexOutOfBoundsException ex){}
				}
			}
		}
		
		viewer.requestRepaint();
	}

	@Override
	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
//		this.active = true;
//		
//		paint(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		this.active = false;
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(e.isAltDown())
		{
			paint(e);
			viewer.requestRepaint();
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}
}
