import Vue from 'vue';
import Modal from './Modal.vue';

export default class CustomDialog extends Vue {
    protected get dialog(): Modal {
        return <Modal>this.$children[0];
    }

    show(): void {
        this.dialog.show();
    }

    hide(): void {
        this.dialog.hide();
    }
}