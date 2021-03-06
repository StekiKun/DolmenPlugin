package org.stekikun.dolmenplugin.editors;

import org.eclipse.swt.graphics.RGB;

/**
 * Constant RGB colors used by the custom editors
 * 
 * @author Stéphane Lescuyer
 */
public interface IColorConstants {
	RGB KEYWORD = new RGB(128, 0, 0);
	RGB KEYWORD_OP = new RGB(128, 0, 128);
	RGB STRING = new RGB(0, 128, 0);
	RGB COMMENT = new RGB(128, 128, 128);
	RGB IDENT = new RGB(0, 0, 128);
	RGB DEFAULT = new RGB(0, 0, 0);
	RGB JAVA_BG = new RGB(236, 247, 236);
}
