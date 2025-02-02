package com.vividsolutions.jump.workbench.imagery.graphic;

/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI 
 * for visualizing and manipulating spatial features with geometry and attributes.
 *
 * Copyright (C) 2003 Vivid Solutions
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
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
/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI 
 * for visualizing and manipulating spatial features with geometry and attributes.
 *
 * JUMP is Copyright (C) 2003 Vivid Solutions
 *
 * This program implements extensions to JUMP and is
 * Copyright (C) 2004 Integrated Systems Analysts, Inc.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * For more information, contact:
 *
 * Integrated Systems Analysts, Inc.
 * 630C Anchors St., Suite 101
 * Fort Walton Beach, Florida 32548
 * USA
 *
 * (850)862-7321
 * www.ashs.isa.com
 */
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.imageio.ImageIO;

import com.vividsolutions.jump.io.CompressedFile;
import com.vividsolutions.jump.util.FileUtil;
import com.vividsolutions.jump.workbench.imagery.ReferencedImageException;


/**
 * An image whose source is a bitmap
 * 
 * Much of this code was donated by Larry Becker and Robert Littlefield of
 * Integrated Systems Analysts, Inc.
 */
public class IOGraphicImage extends AbstractGraphicImage

{
  InputStream is = null;

  public IOGraphicImage(String uri, WorldFile wf) {
    super(uri, wf);
  }

  protected void initImage() throws ReferencedImageException {
    BufferedImage image = getImage();
    if (image != null)
      return;

    try {
      URI uri = new URI(getUri());
      // is = CompressedFile.openFile(uri);
      // image = ImageIO.read(new File(new URI(getUri()))); //.read(is);

      // loading streams is slower than files, hence we check if we really
      // try to open a compressed file first
      if (CompressedFile.isArchive(uri) || CompressedFile.isCompressed(uri)) {
        is = CompressedFile.openFile(uri);
        image = ImageIO.read(is);
        close(is);
      } else {
        // create a File object, native loaders like ecw, mrsid seem to insist
        // on it
        // error was:
        // "Unable to create a valid ImageInputStream for the provided input:"
        // took me two days to debug this.. pfffhhhh
        // if you find this workaround because of the full error string above,
        // that was intentional
        // please send your praises to edgar AT soldin DOT de, would love to
        // hear from you
        image = ImageIO.read(new File(uri));
      }

      if (image == null)
        throw new IOException("ImageIO read returned null");
      // Try to access data and fail fast if not possible
      image.getData().getParent().getDataBuffer();
      setImage(image);
      // infos are difficult to come by, set at least file extension
      setType(FileUtil.getExtension(CompressedFile.getTargetFileWithPath(uri)));
    } catch (Exception e) {
      throw new ReferencedImageException(e);
    } finally {
      // close streams on any failure
      close(is);
    }

  }

  // img flushing in super suffices
  public void dispose() {
    super.dispose();
  }
}