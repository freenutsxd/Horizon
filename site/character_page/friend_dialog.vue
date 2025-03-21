<template>
  <Modal
    :action="'Friends for ' + name"
    :buttons="false"
    dialog-class="modal-dialog-centered modal-lg"
  >
    <div v-show="loading" class="alert alert-info">
      Loading friend information.
    </div>
    <div v-show="error" class="alert alert-danger">{{ error }}</div>
    <template v-if="!loading">
      <div v-if="existing.length" class="card bg-light">
        <div class="card-header">
          <h4>Existing Friendships</h4>
        </div>
        <div class="card-body">
          <div v-for="friend in existing" class="friend-item">
            <character-link :character="friend.source"
              ><img
                class="character-avatar icon"
                :src="avatarUrl(friend.source.name)"
              />
              {{ friend.source.name }}
            </character-link>
            <span class="date"
              >Since: <date-display :time="friend.createdAt"></date-display
            ></span>
            <button
              type="button"
              class="btn btn-danger"
              @click="dissolve(friend)"
            >
              Remove
            </button>
          </div>
        </div>
      </div>
      <div v-if="pending.length" class="card bg-light">
        <div class="card-header">
          <h4>Pending Requests To Character</h4>
        </div>
        <div class="card-body">
          <div v-for="request in pending" class="friend-item">
            <character-link :character="request.source"
              ><img
                class="character-avatar icon"
                :src="avatarUrl(request.source.name)"
              />
              {{ request.source.name }}
            </character-link>
            <span class="date"
              >Sent: <date-display :time="request.createdAt"></date-display
            ></span>
            <button
              type="button"
              class="btn btn-danger"
              @click="cancel(request)"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
      <div v-if="incoming.length" class="card bg-light">
        <div class="card-header">
          <h4>Pending Requests From Character</h4>
        </div>
        <div class="card-body">
          <div v-for="request in incoming" class="friend-item">
            <character-link :character="request.target"
              ><img
                class="character-avatar icon"
                :src="avatarUrl(request.target.name)"
              />
              {{ request.target.name }}
            </character-link>
            <span class="date"
              >Sent: <date-display :time="request.createdAt"></date-display
            ></span>
            <button
              type="button"
              class="btn btn-success acceptFriend"
              @click="accept(request)"
            >
              Accept
            </button>
            <button
              type="button"
              class="btn btn-danger ignoreFriend"
              @click="ignore(request)"
            >
              Ignore
            </button>
          </div>
        </div>
      </div>
      <div class="card bg-light">
        <div class="card-header">
          <h4>Request Friendship</h4>
        </div>
        <div class="card-body">
          <div class="form-inline">
            <label class="control-label" for="friendRequestCharacter"
              >Character:
            </label>
            <character-select
              id="friendRequestCharacter"
              v-model="ourCharacter"
            ></character-select>
            <button
              @click="request"
              class="btn btn-secondary"
              :disable="requesting || !ourCharacter"
            >
              Request
            </button>
          </div>
        </div>
      </div>
    </template>
  </Modal>
</template>

<script lang="ts">
  import { Component, Prop } from '@f-list/vue-ts';
  import CustomDialog from '../../components/custom_dialog';
  import Modal from '../../components/Modal.vue';
  import * as Utils from '../utils';
  import { methods } from './data_store';
  import { Character, Friend, FriendRequest } from './interfaces';

  @Component({
    components: { Modal }
  })
  export default class FriendDialog extends CustomDialog {
    @Prop({ required: true })
    readonly character!: Character;

    ourCharacter = Utils.settings.defaultCharacter;

    incoming: FriendRequest[] = [];
    pending: FriendRequest[] = [];
    existing: Friend[] = [];

    requesting = false;
    loading = true;
    error = '';

    avatarUrl = Utils.avatarURL;

    get name(): string {
      return this.character.character.name;
    }

    async request(): Promise<void> {
      try {
        this.requesting = true;
        const newRequest = await methods.friendRequest(
          this.character.character.id,
          this.ourCharacter
        );
        if (typeof newRequest === 'number')
          this.pending.push({
            id: newRequest,
            source: Utils.characters.find(x => x.id === this.ourCharacter)!,
            target: this.character.character,
            createdAt: Date.now() / 1000
          });
        else this.existing.push(newRequest);
      } catch (e) {
        if (Utils.isJSONError(e)) this.error = <string>e.response.data.error;
        Utils.ajaxError(e, 'Unable to send friend request');
      }
      this.requesting = false;
    }

    async dissolve(friendship: Friend): Promise<void> {
      try {
        await methods.friendDissolve(friendship);
        this.existing.splice(this.existing.indexOf(friendship), 1);
      } catch (e) {
        if (Utils.isJSONError(e)) this.error = <string>e.response.data.error;
        Utils.ajaxError(e, 'Unable to dissolve friendship');
      }
    }

    async accept(request: FriendRequest): Promise<void> {
      try {
        const friend = await methods.friendRequestAccept(request);
        this.existing.push(friend);
        this.incoming.splice(this.incoming.indexOf(request), 1);
      } catch (e) {
        if (Utils.isJSONError(e)) this.error = <string>e.response.data.error;
        Utils.ajaxError(e, 'Unable to accept friend request');
      }
    }

    async cancel(request: FriendRequest): Promise<void> {
      try {
        await methods.friendRequestCancel(request);
        this.pending.splice(this.pending.indexOf(request), 1);
      } catch (e) {
        if (Utils.isJSONError(e)) this.error = <string>e.response.data.error;
        Utils.ajaxError(e, 'Unable to cancel friend request');
      }
    }

    async ignore(request: FriendRequest): Promise<void> {
      try {
        await methods.friendRequestIgnore(request);
        this.incoming.splice(this.incoming.indexOf(request), 1);
      } catch (e) {
        if (Utils.isJSONError(e)) this.error = <string>e.response.data.error;
        Utils.ajaxError(e, 'Unable to ignore friend request');
      }
    }

    async show(): Promise<void> {
      super.show();
      try {
        this.loading = true;
        const friendData = await methods.characterFriends(
          this.character.character.id
        );
        this.incoming = friendData.incoming;
        this.pending = friendData.pending;
        this.existing = friendData.existing;
      } catch (e) {
        Utils.ajaxError(e, 'Unable to load character friendship information');
      }
      this.loading = false;
    }
  }
</script>
