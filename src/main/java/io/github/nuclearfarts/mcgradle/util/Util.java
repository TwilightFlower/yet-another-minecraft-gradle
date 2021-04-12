package io.github.nuclearfarts.mcgradle.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.LocalVariableDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public final class Util {
	public static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	private Util() {}
	
	public static final URI jarFsUri(Path p) {
		return URI.create("jar:" + p.toUri().toString());
	}
	
	public static URL uncheckedUrl(String str) {
		try {
			return new URL(str);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void download(URL url, Path to) {
		byte[] buf = new byte[4096];
		try(InputStream download = url.openStream()) {
			try(OutputStream out = Files.newOutputStream(to)) {
				int read;
				while((read = download.read(buf)) != -1) {
					out.write(buf, 0, read);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("error downloading", e);
		}
	}
	
	public static byte[] readStream(InputStream stream) throws IOException {
		ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int read;
		while((read = stream.read(buf)) != -1) {
			outBuf.write(buf, 0, read);
		}
		return outBuf.toByteArray();
	}
	
	// these two methods copied from loom
	public static IMappingProvider create(TinyTree mappings, String from, String to, boolean remapLocalVariables) {
		return (acceptor) -> {
			for (ClassDef classDef : mappings.getClasses()) {
				String className = classDef.getName(from);
				acceptor.acceptClass(className, classDef.getName(to));

				for (FieldDef field : classDef.getFields()) {
					acceptor.acceptField(memberOf(className, field.getName(from), field.getDescriptor(from)), field.getName(to));
				}

				for (MethodDef method : classDef.getMethods()) {
					IMappingProvider.Member methodIdentifier = memberOf(className, method.getName(from), method.getDescriptor(from));
					acceptor.acceptMethod(methodIdentifier, method.getName(to));

					if (remapLocalVariables) {
						for (ParameterDef parameter : method.getParameters()) {
							acceptor.acceptMethodArg(methodIdentifier, parameter.getLocalVariableIndex(), parameter.getName(to));
						}

						for (LocalVariableDef localVariable : method.getLocalVariables()) {
							acceptor.acceptMethodVar(methodIdentifier, localVariable.getLocalVariableIndex(),
											localVariable.getLocalVariableStartOffset(), localVariable.getLocalVariableTableIndex(),
											localVariable.getName(to));
						}
					}
				}
			}
		};
	}
	
	private static IMappingProvider.Member memberOf(String className, String memberName, String descriptor) {
		return new IMappingProvider.Member(className, memberName, descriptor);
	}
	
	@SuppressWarnings("serial")
	public static class LambdasSuckException extends RuntimeException {
		public final IOException exc;
		public LambdasSuckException(IOException exc) {
			this.exc = exc;
		}
	}
}
