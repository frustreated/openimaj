package org.openimaj.demos.sandbox.asm;

import java.util.ArrayList;
import java.util.List;

import org.openimaj.image.DisplayUtilities;
import org.openimaj.image.FImage;
import org.openimaj.math.geometry.line.Line2d;
import org.openimaj.math.geometry.point.Point2d;
import org.openimaj.math.geometry.point.Point2dImpl;
import org.openimaj.math.geometry.shape.PointDistributionModel;
import org.openimaj.math.geometry.shape.PointDistributionModel.Constraint;
import org.openimaj.math.geometry.shape.PointList;
import org.openimaj.math.geometry.shape.PointListConnections;
import org.openimaj.util.pair.IndependentPair;

import Jama.Matrix;

public class ActiveShapeModel {
	int k;
	int m;
	float scale;
	PointListConnections connections;
	PointDistributionModel pdm;
	private PixelProfileModel[] ppms;
	int maxIter = 25;
	
	public ActiveShapeModel(int k, int m, float scale, PointListConnections connections, PointDistributionModel pdm, PixelProfileModel[] ppms) {
		this.k = k;
		this. m = m;
		this.scale = scale;
		this.connections = connections;
		this.pdm = pdm;
		this.ppms = ppms;
	}
	
	public static ActiveShapeModel trainModel(int k, int m, float scale, int numComponents, PointListConnections connections, List<IndependentPair<PointList, FImage>> data, Constraint constraint) {
		int nPoints = data.get(0).firstObject().size();
		
		PixelProfileModel[] ppms = new PixelProfileModel[nPoints];
		for (int i=0; i<data.size(); i++) {
			for (int j=0; j<nPoints; j++) {
				if (ppms[j] == null) {
					ppms[j] = new PixelProfileModel(2*k + 1);
				}
			
				PointList pl = data.get(i).firstObject();
				float lineScale = scale * pl.computeIntrinsicScale();
				
				ppms[j].addSample(data.get(i).secondObject(), connections.calculateNormalLine(j, pl, lineScale));
			}
		}
		
		List<PointList> pls = new ArrayList<PointList>();
		for (IndependentPair<PointList, FImage> i : data)
			pls.add(i.firstObject());
		
		PointDistributionModel pdm = new PointDistributionModel(constraint, pls);
		pdm.setNumComponents(numComponents);
		
		return new ActiveShapeModel(k, m, scale, connections, pdm, ppms);
	}
	
	public static class IterationResult {
		public double fit;
		public PointList shape;
		public Matrix pose;

		public IterationResult(Matrix pose, PointList shape, double fit) {
			this.pose = pose;
			this.shape = shape;
			this.fit = fit;
		}
	}
	
	public IterationResult performIteration(FImage image, Matrix pose, PointList currentShape) {
		float scale2 = (2*m + 1) * scale * currentShape.computeIntrinsicScale() / (2*k + 1); 
		
		PointList newShape = new PointList();
		
		int inliers = 0;
		int outliers = 0;
		for (int i=0; i<ppms.length; i++) {
			Line2d testLine = connections.calculateNormalLine(i, currentShape, scale2);
			
			Point2dImpl newBest = ppms[i].computeNewBest(image, testLine, 2*m+1);
			newShape.points.add( newBest );
			
			double percentageFromStart = Line2d.distance(testLine.begin, newBest) / testLine.calculateLength();
			if (percentageFromStart > 0.25 && percentageFromStart < 0.75)
				inliers++;
			else 
				outliers++;
		}

		IndependentPair<Matrix, double[]> newModelParams = pdm.fitModel(newShape);
		pose = newModelParams.firstObject();
		currentShape = pdm.generateNewShape(newModelParams.secondObject()).transform(pose);
		
		return new IterationResult(pose, currentShape, ((double)inliers) / ((double)(inliers + outliers)));
	}

	public FImage drawShapeAndNormals(FImage frame, PointList shape) {
		FImage image = frame.clone();
		
		image.drawImage(image, 0, 0);
		image.drawLines(connections.getLines(shape), 1, 1f);
		
		float shapeScale = shape.computeIntrinsicScale();
		for (Point2d pt : shape) {
			Line2d normal = connections.calculateNormalLine(pt, shape, scale * shapeScale);
			if (normal != null) image.drawLine(normal, 1, 0.5f);
		}
		
		return image;
	}
	
	public IterationResult fit(FImage image, Matrix initialPose, PointList initialShape) {
		IterationResult ir = performIteration(image, initialPose, initialShape);
		int count = 0;
		while (ir.fit < 0.9 && count < maxIter) {
			ir = performIteration(image, ir.pose, ir.shape);
			count++;
			
			//DisplayUtilities.displayName(drawShapeAndNormals(image, ir.shape), "shape", true);
		}
		
		return ir;
	}
	
	public PointDistributionModel getPDM() {
		return pdm;
	}

	public PixelProfileModel[] getPPMs() {
		return ppms;
	}
}