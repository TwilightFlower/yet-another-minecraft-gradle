package io.github.nuclearfarts.mcgradle.sources;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class LinemapperTransformer extends ClassVisitor {
	private final int[] lmap;
	public LinemapperTransformer(ClassVisitor classVisitor, int[] lmap) {
		super(Opcodes.ASM9, classVisitor);
		this.lmap = lmap;
	}
	
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return new LinemapperMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions));
	}
	
	private final class LinemapperMethodVisitor extends MethodVisitor {
		public LinemapperMethodVisitor(MethodVisitor delegate) {
			super(Opcodes.ASM9, delegate);
		}
		
		@Override
		public void visitLineNumber(int number, Label on) {
			if(number < lmap.length) {
				super.visitLineNumber(lmap[number], on);
			} else {
				super.visitLineNumber(number, on);
			}
		}
	}
}
