package net.fabricmc.fabric.api.renderer.v1;

public interface RendererAccess {
    RendererAccess INSTANCE = new RendererAccess() {
        @Override
        public boolean hasRenderer() {
            return false;
        }

        @Override
        public Renderer getRenderer() {
            return null;
        }

        @Override
        public void registerRenderer(Renderer renderer) {}
    };

    boolean hasRenderer();
    Renderer getRenderer();
    void registerRenderer(Renderer renderer);
}
